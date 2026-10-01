package ra.edu.productservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record CreateProductRequest(
        @NotBlank(message = "name is required")
        String name,

        String slug,

        String description,

        @NotNull(message = "categoryId is required")
        UUID categoryId,

        @NotNull(message = "brandId is required")
        UUID brandId,

        @NotNull(message = "basePrice is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "basePrice must be non-negative")
        BigDecimal basePrice,

        String status,

        @NotEmpty(message = "At least one SKU is required")
        @Valid
        List<CreateSkuRequest> skus
) {
}
