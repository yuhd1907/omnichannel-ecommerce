package ra.edu.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body cho POST /api/v1/admin/inventory/{skuCode}/adjustments.
 *
 * delta > 0 : nhập thêm hàng
 * delta < 0 : xuất/điều chỉnh giảm (không được làm available_qty âm — service kiểm tra)
 */
public record AdjustInventoryRequest(

        @NotNull(message = "delta is required")
        Long delta,

        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason
) {}
