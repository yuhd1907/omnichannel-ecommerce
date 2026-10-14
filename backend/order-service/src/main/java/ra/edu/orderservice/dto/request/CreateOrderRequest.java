package ra.edu.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * Body cá»§a POST /api/v1/orders.
 * Server tá»± tÃ­nh giÃ¡ â€” client chá»‰ gá»­i skuCode + quantity.
 */
public record CreateOrderRequest(

        @NotBlank(message = "channel is required")
        String channel,          // WEB | MOBILE â€” validate thÃªm á»Ÿ service

        @NotNull(message = "addressId is required")
        UUID addressId,

        @NotNull(message = "items is required")
        @Size(min = 1, max = 50, message = "Order must have 1-50 items")
        @Valid
        List<OrderItemRequest> items
) {

    public record OrderItemRequest(

            @NotBlank(message = "skuCode is required")
            String skuCode,

            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "quantity must be at least 1")
        // Chan go nham / lam dung (vd 1 ty chiec giu het kho); don si lon hon di kenh khac
        @Max(value = 100, message = "quantity must be at most 100")
            Long quantity
    ) {}
}
