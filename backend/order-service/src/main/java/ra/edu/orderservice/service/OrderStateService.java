package ra.edu.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.common.response.ErrorCode;
import ra.edu.orderservice.entity.Order;
import ra.edu.orderservice.entity.OrderItem;
import ra.edu.orderservice.entity.OutboxEvent;
import ra.edu.orderservice.exception.BusinessException;
import ra.edu.orderservice.repository.InventoryRepository;
import ra.edu.orderservice.repository.OrderRepository;
import ra.edu.orderservice.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mọi chuyển trạng thái rời PENDING đi qua đây — dùng chung cho hủy đơn (user/admin) và kết quả
 * thanh toán (PaymentEventListener). Bean riêng để @Transactional có hiệu lực khi được gọi từ
 * bean khác (gọi nội bộ trong cùng class sẽ bỏ qua proxy transaction).
 *
 * Quy tắc: transitionFromPending TRƯỚC, đụng kho SAU. Bên thua race nhận 0 và không chạm kho,
 * nên một đơn không thể vừa confirm vừa release. Gọi lại với event cũ cũng nhận 0 -> idempotent,
 * không cần bảng chống trùng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateService {

    private final OrderRepository       orderRepository;
    private final InventoryRepository   inventoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper            jsonMapper;

    /**
     * PENDING -> CANCELLED và nhả kho.
     *
     * @return false nếu đơn không còn PENDING (đã CONFIRMED/CANCELLED) — không làm gì.
     */
    @Transactional
    public boolean cancelAndRelease(UUID orderId, String paymentStatus, String reason) {
        if (orderRepository.transitionFromPending(orderId, "CANCELLED", paymentStatus) == 0) {
            return false;
        }

        Order order = loadWithItems(orderId);
        for (OrderItem item : order.getItems()) {
            if (inventoryRepository.release(item.getSkuCode(), item.getQuantity()) == 0) {
                // Không nên xảy ra nếu dữ liệu nhất quán -> rollback cả transition để điều tra
                log.error("Release inventory failed: sku={}, qty={}, orderId={}",
                        item.getSkuCode(), item.getQuantity(), orderId);
                throw new BusinessException("INVENTORY_RELEASE_FAILED",
                        "Could not release inventory for SKU: " + item.getSkuCode(),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("customerEmail", order.getCustomerEmail());
        payload.put("reason", reason);
        writeOutbox(order, "ORDER_CANCELLED", payload);

        log.info("Order cancelled: id={}, paymentStatus={}, reason={}", orderId, paymentStatus, reason);
        return true;
    }

    /**
     * PENDING -> CONFIRMED / PAID và chuyển hàng đã giữ thành đã bán.
     *
     * @return false nếu đơn không còn PENDING (vd đã bị hủy/hết hạn trước khi tiền về).
     *         Khi đó payment-service tự đánh dấu needs_refund lúc nhận order.cancelled.
     */
    @Transactional
    public boolean confirmPaid(UUID orderId) {
        return confirmPaid(orderId, null);
    }

    /** @param method phương thức online đã trả (VNPAY, MOCK...) lấy từ payload event; null nếu không rõ. */
    @Transactional
    public boolean confirmPaid(UUID orderId, String method) {
        return confirm(orderId, "PAID", method);
    }

    /**
     * Khách chọn thanh toán khi nhận hàng: PENDING -> CONFIRMED nhưng vẫn UNPAID; hàng giữ chuyển
     * thành đã bán như đơn trả trước. Tiền ghi nhận khi admin chuyển COMPLETED (markPaidIfUnpaid).
     */
    @Transactional
    public boolean confirmCod(UUID orderId) {
        return confirm(orderId, "UNPAID", "COD");
    }

    private boolean confirm(UUID orderId, String paymentStatus, String method) {
        if (orderRepository.transitionFromPending(orderId, "CONFIRMED", paymentStatus) == 0) {
            log.warn("Received payment for order that is no longer PENDING, ignore (orderId={})", orderId);
            return false;
        }
        if (method != null) {
            orderRepository.setPaymentMethod(orderId, method);
        }

        Order order = loadWithItems(orderId);
        for (OrderItem item : order.getItems()) {
            if (inventoryRepository.confirm(item.getSkuCode(), item.getQuantity()) == 0) {
                log.error("Confirm inventory failed: sku={}, qty={}, orderId={}",
                        item.getSkuCode(), item.getQuantity(), orderId);
                throw new BusinessException("INVENTORY_CONFIRM_FAILED",
                        "Could not confirm inventory for SKU: " + item.getSkuCode(),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("customerEmail", order.getCustomerEmail());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("paymentMethod", order.getPaymentMethod());
        writeOutbox(order, "ORDER_CONFIRMED", payload);

        log.info("Order confirmed: id={}, paymentStatus={}, method={}", orderId, paymentStatus, method);
        return true;
    }

    /** Trạng thái đích -> trạng thái nguồn bắt buộc. Chỉ cho đi tiến, không nhảy cóc. */
    private static final Map<String, String> FULFILLMENT_FROM = Map.of(
            "SHIPPING",  "CONFIRMED",
            "COMPLETED", "SHIPPING");

    private static final Map<String, String> FULFILLMENT_EVENT = Map.of(
            "SHIPPING",  "ORDER_SHIPPED",
            "COMPLETED", "ORDER_COMPLETED");

    /**
     * Admin chuyển đơn đã thanh toán sang giao hàng / hoàn tất. Không đụng kho
     * (kho đã chuyển reserved -> sold lúc confirmPaid).
     *
     * @throws BusinessException 400 nếu target không hợp lệ, 404 nếu không có đơn,
     *                           409 nếu đơn không ở trạng thái nguồn tương ứng.
     */
    @Transactional
    public Order advanceFulfillment(UUID orderId, String target) {
        String to = target == null ? "" : target.trim().toUpperCase();
        String from = FULFILLMENT_FROM.get(to);
        if (from == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Target status must be SHIPPING or COMPLETED, got: " + target,
                    HttpStatus.BAD_REQUEST);
        }

        if (orderRepository.transition(orderId, from, to) == 0) {
            Order current = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(
                            "ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot change order from " + current.getStatus() + " to " + to
                            + " (requires " + from + ")",
                    HttpStatus.CONFLICT);
        }

        // Đơn COD: giao xong = shipper đã thu tiền mặt
        if ("COMPLETED".equals(to) && orderRepository.markPaidIfUnpaid(orderId) > 0) {
            log.info("Cash collected on delivery: orderId={}", orderId);
        }

        Order order = loadWithItems(orderId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("customerEmail", order.getCustomerEmail());
        payload.put("status", to);
        payload.put("paymentMethod", order.getPaymentMethod());
        writeOutbox(order, FULFILLMENT_EVENT.get(to), payload);

        log.info("Order fulfillment advanced: id={}, {} -> {}", orderId, from, to);
        return order;
    }

    private Order loadWithItems(UUID orderId) {
        // Persistence context đã bị clear bởi transitionFromPending -> nạp lại bản mới từ DB.
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order vanished after transition: " + orderId));
    }

    private void writeOutbox(Order order, String eventType, Map<String, Object> payload) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(order.getId())
                .eventType(eventType)
                .payload(jsonMapper.writeValueAsString(payload))
                .build());
    }
}
