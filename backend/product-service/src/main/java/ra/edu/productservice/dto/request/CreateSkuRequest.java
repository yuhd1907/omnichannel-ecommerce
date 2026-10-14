package ra.edu.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
public record CreateSkuRequest(
        @NotBlank(message = "skuCode is required")
        // Ma SKU dung lam khoa giua product va inventory: khong khoang trang / ky tu dac biet
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,99}$",
                message = "skuCode must be 2-100 chars: letters, digits, '.', '_' or '-'")
        String skuCode,

        Map<String, Object> attributes,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than 0")
        BigDecimal price,

        String imageUrl
) {
}
