package ra.edu.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record UpdateProductRequest(
        @jakarta.validation.constraints.Size(min = 1, max = 255, message = "name must be 1-255 characters")
        String name,
        String description,
        UUID categoryId,
        UUID brandId,
        @DecimalMin(value = "0.0", inclusive = false, message = "basePrice must be greater than 0")
        BigDecimal basePrice,
        @jakarta.validation.constraints.Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "status must be ACTIVE or INACTIVE")
        String status
) {
}
