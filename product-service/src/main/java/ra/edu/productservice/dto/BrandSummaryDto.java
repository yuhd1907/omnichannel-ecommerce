package ra.edu.productservice.dto;

import lombok.Builder;
import ra.edu.productservice.entity.Brand;

import java.util.UUID;

@Builder
public record BrandSummaryDto(
        UUID id,
        String name,
        String slug
) {
    public static BrandSummaryDto from(Brand brand) {
        if (brand == null) return null;
        return new BrandSummaryDto(brand.getId(), brand.getName(), brand.getSlug());
    }
}
