package ra.edu.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Dùng cho danh sách — không cần items chi tiết. */
public record OrderSummaryDto(
        UUID id,
        String channel,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        Instant createdAt
) {}
