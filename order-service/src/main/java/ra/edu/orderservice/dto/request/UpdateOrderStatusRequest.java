package ra.edu.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body cho PATCH /api/v1/admin/orders/{id}/status.
 * status: SHIPPING (từ CONFIRMED) hoặc COMPLETED (từ SHIPPING).
 */
public record UpdateOrderStatusRequest(

        @NotBlank(message = "status is required")
        String status
) {}
