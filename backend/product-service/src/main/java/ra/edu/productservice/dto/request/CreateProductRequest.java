package ra.edu.productservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
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
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        // Tuy chon (bo trong -> sinh tu name). Co thi chi gom chu thuong, so, gach noi: "iphone-15-pro"
        @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$", message = "slug must be lowercase letters, digits and hyphens")
        @Size(max = 255, message = "slug must be at most 255 characters")
        String slug,

        String description,

        @NotNull(message = "categoryId is required")
        UUID categoryId,

        @NotNull(message = "brandId is required")
        UUID brandId,

        @NotNull(message = "basePrice is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "basePrice must be greater than 0")
        BigDecimal basePrice,

        @Pattern(regexp = "^$|^(ACTIVE|INACTIVE)$", message = "status must be ACTIVE or INACTIVE")
        String status,

        @NotEmpty(message = "At least one SKU is required")
        @Valid
        List<CreateSkuRequest> skus
) {
}
