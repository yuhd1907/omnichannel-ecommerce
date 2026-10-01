package ra.edu.productservice.dto;

import lombok.Builder;
import ra.edu.productservice.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record ProductSummaryDto(
        UUID id,
        String name,
        String slug,
        String description,
        CategorySummaryDto category,
        BrandSummaryDto brand,
        BigDecimal basePrice,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductSummaryDto from(Product product) {
        if (product == null) return null;
        return new ProductSummaryDto(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                CategorySummaryDto.from(product.getCategory()),
                BrandSummaryDto.from(product.getBrand()),
                product.getBasePrice(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
