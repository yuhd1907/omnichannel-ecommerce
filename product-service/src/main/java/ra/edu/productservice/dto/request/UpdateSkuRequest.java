package ra.edu.productservice.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
public record UpdateSkuRequest(
        @Positive(message = "price must be positive")
        BigDecimal price,

        String imageUrl,

        Map<String, Object> attributes
) {
}
