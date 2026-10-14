package ra.edu.productservice.dto;

import lombok.Builder;
import ra.edu.productservice.entity.ProductSku;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record ProductSkuDto(
        UUID id,
        String skuCode,
        Map<String, Object> attributes,
        BigDecimal price,
        String imageUrl,
        Instant updatedAt
) {
    public static ProductSkuDto from(ProductSku sku) {
        if (sku == null) return null;
        return new ProductSkuDto(
                sku.getId(),
                sku.getSkuCode(),
                sku.getAttributes(),
                sku.getPrice(),
                sku.getImageUrl(),
                sku.getUpdatedAt()
        );
    }
}
