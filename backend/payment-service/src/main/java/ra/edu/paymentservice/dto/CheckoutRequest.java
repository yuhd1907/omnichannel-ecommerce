package ra.edu.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank String method
) {
}
