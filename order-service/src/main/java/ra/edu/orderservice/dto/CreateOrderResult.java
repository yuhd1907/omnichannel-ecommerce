package ra.edu.orderservice.dto;

/**
 * Kết quả tạo đơn.
 *
 * @param replayed true nếu request mang Idempotency-Key đã dùng trước đó: trả lại đơn cũ,
 *                 không tạo đơn mới (controller trả 200 thay vì 201).
 */
public record CreateOrderResult(OrderDto order, boolean replayed) {
}
