package ra.edu.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDto(
        UUID id,
        UUID userId,
        String channel,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        String shippingRecipientName,
        String shippingPhone,
        String shippingAddress,
        Instant createdAt,
        List<OrderItemDto> items
) {

    public record OrderItemDto(
            UUID id,
            String skuCode,
            String productName,
            BigDecimal unitPrice,
            long quantity,
            BigDecimal subtotal
    ) {}
}
