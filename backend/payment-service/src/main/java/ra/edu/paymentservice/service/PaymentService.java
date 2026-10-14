package ra.edu.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.common.response.ErrorCode;
import ra.edu.paymentservice.dto.CheckoutRequest;
import ra.edu.paymentservice.dto.PaymentView;
import ra.edu.paymentservice.entity.OutboxEvent;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.exception.BusinessException;
import ra.edu.paymentservice.gateway.PaymentGatewayAdapter;
import ra.edu.paymentservice.repository.OutboxEventRepository;
import ra.edu.paymentservice.repository.PaymentRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** Thanh toán khi nhận hàng: không qua cổng, không có checkoutUrl. */
    static final String METHOD_COD = "COD";

    private final PaymentRepository paymentRepository;
    private final List<PaymentGatewayAdapter> adapters;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    /** Map tra cứu nhanh adapter theo method (MOCK, VNPAY, ...). */
    private Map<String, PaymentGatewayAdapter> adapterMap() {
        return adapters.stream()
                .collect(Collectors.toMap(a -> a.method().toUpperCase(), Function.identity()));
    }

    /**
     * POST /api/v1/orders/{orderId}/payments
     * Idempotent: nếu payment còn PENDING thì trả lại đúng payment đó kèm checkoutUrl.
     */
    @Transactional
    public PaymentView checkout(UUID orderId, UUID userId, CheckoutRequest request) {
        return checkout(orderId, userId, request, null);
    }

    /** @param clientIp IP người mua (VNPAY bắt buộc gửi vnp_IpAddr); null nếu không rõ. */
    @Transactional
    public PaymentView checkout(UUID orderId, UUID userId, CheckoutRequest request, String clientIp) {
        // 1. Tìm payment theo orderId — nếu chưa có thì order.created event chưa relay tới
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_READY,
                        "Payment record not found. Order may still be processing, please retry shortly.",
                        HttpStatus.CONFLICT));

        // 2. Chủ đơn hoặc admin — admin không bị từ chối ở đây (security layer lo)
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ACCESS_DENIED,
                    "You are not the owner of this order.",
                    HttpStatus.FORBIDDEN);
        }

        // 3. Trạng thái SUCCESS → không cho thanh toán lại
        if ("SUCCESS".equals(payment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ALREADY_COMPLETED,
                    "This order has already been paid.",
                    HttpStatus.CONFLICT);
        }

        // 4. Trạng thái FAILED → đơn đã hủy hoặc hết hạn
        if ("FAILED".equals(payment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CLOSED,
                    "Order is cancelled or expired. Payment is no longer accepted.",
                    HttpStatus.CONFLICT);
        }

        String methodKey = request.method().trim().toUpperCase();

        // 5a. COD: xác nhận đơn ngay qua event, tiền thu khi giao (order.completed -> SUCCESS)
        if (METHOD_COD.equals(methodKey)) {
            return acceptCod(payment);
        }
        // Đã chọn COD thì đơn đã CONFIRMED, không đổi sang trả online nữa
        if (METHOD_COD.equals(payment.getMethod())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_METHOD_LOCKED,
                    "This order uses cash on delivery; the payment method can no longer be changed.",
                    HttpStatus.CONFLICT);
        }

        // 5b. Lấy adapter cho method online — không có → 400
        PaymentGatewayAdapter adapter = adapterMap().get(methodKey);
        if (adapter == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Payment method '" + request.method() + "' is not supported.",
                    HttpStatus.BAD_REQUEST);
        }

        // 6. Lưu method (có thể đổi method khi còn PENDING), tạo checkoutUrl
        payment.setMethod(methodKey);
        paymentRepository.save(payment);

        String checkoutUrl = adapter.buildCheckoutUrl(payment, clientIp);
        log.info("Checkout initiated: orderId={}, userId={}, method={}", orderId, userId, methodKey);

        return toView(payment, checkoutUrl);
    }

    /**
     * Ghi method COD + outbox PAYMENT_COD_ACCEPTED trong cùng transaction (không mất event nếu
     * broker chết). Gọi lại khi đã là COD thì trả về luôn, không phát event lần hai.
     */
    private PaymentView acceptCod(Payment payment) {
        if (METHOD_COD.equals(payment.getMethod())) {
            return toView(payment, null);
        }
        payment.setMethod(METHOD_COD);
        paymentRepository.save(payment);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("paymentId", payment.getId());
        event.put("orderId", payment.getOrderId());
        event.put("amount", payment.getAmount());
        event.put("method", METHOD_COD);
        event.put("occurredAt", Instant.now().toString());
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Payment")
                .aggregateId(payment.getId())
                .eventType("PAYMENT_COD_ACCEPTED")
                .payload(jsonMapper.writeValueAsString(event))
                .build());

        log.info("COD accepted: orderId={}, amount={}", payment.getOrderId(), payment.getAmount());
        return toView(payment, null);
    }

    /**
     * GET /api/v1/orders/{orderId}/payments
     * Chủ đơn hoặc ADMIN.
     */
    @Transactional(readOnly = true)
    public PaymentView getPayment(UUID orderId, UUID userId, boolean isAdmin) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found for this order.",
                        HttpStatus.NOT_FOUND));

        if (!isAdmin && !payment.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ACCESS_DENIED,
                    "You are not the owner of this order.",
                    HttpStatus.FORBIDDEN);
        }

        // checkoutUrl chỉ hiển thị khi còn PENDING và đã chọn method
        String checkoutUrl = null;
        if ("PENDING".equals(payment.getStatus()) && payment.getMethod() != null) {
            PaymentGatewayAdapter adapter = adapterMap().get(payment.getMethod().toUpperCase());
            if (adapter != null) {
                checkoutUrl = adapter.buildCheckoutUrl(payment);
            }
        }

        return toView(payment, checkoutUrl);
    }

    private PaymentView toView(Payment payment, String checkoutUrl) {
        return new PaymentView(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                checkoutUrl);
    }
}
