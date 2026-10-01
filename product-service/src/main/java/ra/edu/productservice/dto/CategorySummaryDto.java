package ra.edu.productservice.dto;

import lombok.Builder;
import ra.edu.productservice.entity.Category;

import java.util.UUID;

@Builder
public record CategorySummaryDto(
        UUID id,
        String name,
        String slug
) {
    public static CategorySummaryDto from(Category category) {
        if (category == null) return null;
        return new CategorySummaryDto(category.getId(), category.getName(), category.getSlug());
    }
}
