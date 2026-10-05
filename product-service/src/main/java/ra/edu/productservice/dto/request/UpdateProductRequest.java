package ra.edu.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record UpdateProductRequest(
        String name,
        String description,
        UUID categoryId,
        UUID brandId,
        @DecimalMin(value = "0.0", inclusive = true, message = "basePrice must be non-negative")
        BigDecimal basePrice,
        String status
) {
}
