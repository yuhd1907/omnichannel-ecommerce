package ra.edu.paymentservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentView(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String method,
        String status,
        String checkoutUrl
) {
}
