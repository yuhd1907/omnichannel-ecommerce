package ra.edu.orderservice.service;

import org.springframework.data.domain.Pageable;
import ra.edu.common.response.PageData;
import ra.edu.orderservice.dto.OrderDto;
import ra.edu.orderservice.dto.OrderSummaryDto;
import ra.edu.orderservice.dto.request.CreateOrderRequest;

import java.util.UUID;

public interface OrderService {

    /** Tạo đơn hàng mới. userId lấy từ JWT, không tin client. */
    OrderDto createOrder(UUID userId, CreateOrderRequest request);

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
     * Đơn không phải PENDING → 409 CANNOT_CANCEL.
     * Đơn không của user (và không phải ADMIN) → 404.
     */
    OrderDto cancelOrder(UUID orderId, UUID userId, boolean isAdmin);
}
