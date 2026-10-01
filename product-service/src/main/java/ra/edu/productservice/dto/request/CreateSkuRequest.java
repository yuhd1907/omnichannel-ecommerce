package ra.edu.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
public record CreateSkuRequest(
        @NotBlank(message = "skuCode is required")
        String skuCode,

        Map<String, Object> attributes,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "price must be non-negative")
        BigDecimal price,

        String imageUrl
) {
}
