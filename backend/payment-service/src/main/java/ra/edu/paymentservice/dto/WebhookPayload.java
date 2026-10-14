package ra.edu.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/** Body webhook từ cổng thanh toán. status: SUCCESS | FAILED. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(
        UUID orderId,
        String gatewayTxnId,
        String status,
        BigDecimal amount
) {
}
