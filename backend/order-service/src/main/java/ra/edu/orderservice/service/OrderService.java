package ra.edu.orderservice.service;

import org.springframework.data.domain.Pageable;
import ra.edu.common.response.PageData;
import ra.edu.orderservice.dto.CreateOrderResult;
import ra.edu.orderservice.dto.OrderDto;
import ra.edu.orderservice.dto.OrderSummaryDto;
import ra.edu.orderservice.dto.request.CreateOrderRequest;

import java.util.UUID;

public interface OrderService {

    /**
     * Tạo đơn hàng mới. userId và email lấy từ JWT, không tin client.
     *
     * @param customerEmail claim email của token, lưu snapshot để gửi mail; có thể null
     */
    default OrderDto createOrder(UUID userId, String customerEmail, CreateOrderRequest request) {
        return createOrder(userId, customerEmail, request, null).order();
    }

    /**
     * Tạo đơn có hỗ trợ Idempotency-Key (header do client sinh, vd UUID).
     * <ul>
     *   <li>Key chưa dùng: tạo đơn như bình thường, ghi key cùng transaction.</li>
     *   <li>Key đã dùng, cùng nội dung: trả lại đúng đơn cũ, {@code replayed = true}.</li>
     *   <li>Key đã dùng, khác nội dung: 422 IDEMPOTENCY_KEY_REUSED.</li>
     * </ul>
     *
     * @param idempotencyKey null = không dùng cơ chế này (hành vi cũ)
     */
    CreateOrderResult createOrder(UUID userId, String customerEmail, CreateOrderRequest request, String idempotencyKey);

    /**
     * Lấy chi tiết đơn hàng.
     * Chủ đơn hoặc ADMIN mới thấy; người khác nhận 404 (không lộ sự tồn tại của đơn).
     *
     * @param isAdmin true nếu caller có role ADMIN
     */
    OrderDto getOrder(UUID orderId, UUID userId, boolean isAdmin);

    /** Danh sách đơn của user hiện tại, sort created_at DESC. */
    PageData<OrderSummaryDto> listMyOrders(UUID userId, Pageable pageable);

    /**
     * Hủy đơn PENDING: đổi status → CANCELLED, hoàn kho trong cùng transaction.
     * Đơn không phải PENDING → 409 ORDER_NOT_CANCELLABLE.
     * Đơn không của user (và không phải ADMIN) → 404.
     */
    OrderDto cancelOrder(UUID orderId, UUID userId, boolean isAdmin);

    /**
     * Admin: danh sách mọi đơn, lọc theo status nếu có. Sort created_at DESC.
     */
    PageData<OrderSummaryDto> listAllOrders(String status, Pageable pageable);

    /**
     * Admin: chuyển đơn CONFIRMED -> SHIPPING hoặc SHIPPING -> COMPLETED.
     * Sai thứ tự -> 409 INVALID_STATUS_TRANSITION; không có đơn -> 404.
     */
    OrderDto updateStatusByAdmin(UUID orderId, String targetStatus);
}
