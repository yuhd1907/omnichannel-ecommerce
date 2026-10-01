package ra.edu.productservice.dto;

import lombok.Builder;
import ra.edu.productservice.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Builder
public record ProductDetailDto(
        UUID id,
        String name,
        String slug,
        String description,
        CategorySummaryDto category,
        BrandSummaryDto brand,
        BigDecimal basePrice,
        String status,
        Instant createdAt,
        Instant updatedAt,
        List<ProductSkuDto> skus
) {
    public static ProductDetailDto from(Product product) {
        if (product == null) return null;
        List<ProductSkuDto> skuDtos = product.getSkus() != null
                ? product.getSkus().stream().map(ProductSkuDto::from).toList()
                : Collections.emptyList();

        return new ProductDetailDto(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                CategorySummaryDto.from(product.getCategory()),
                BrandSummaryDto.from(product.getBrand()),
                product.getBasePrice(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                skuDtos
        );
    }
}
