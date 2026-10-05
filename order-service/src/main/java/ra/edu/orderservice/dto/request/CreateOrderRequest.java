package ra.edu.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * Body của POST /api/v1/orders.
 * Server tự tính giá — client chỉ gửi skuCode + quantity.
 */
public record CreateOrderRequest(

        @NotBlank(message = "channel is required")
        String channel,          // WEB | MOBILE — validate thêm ở service

        @NotNull(message = "addressId is required")
        UUID addressId,

        @NotNull(message = "items is required")
        @Size(min = 1, message = "Order must have at least 1 item")
        @Valid
        List<OrderItemRequest> items
) {

    public record OrderItemRequest(

            @NotBlank(message = "skuCode is required")
            String skuCode,

            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "quantity must be at least 1")
            Long quantity
    ) {}
}
