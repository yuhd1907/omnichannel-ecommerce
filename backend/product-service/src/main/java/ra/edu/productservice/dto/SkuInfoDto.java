package ra.edu.productservice.dto;

import java.math.BigDecimal;

/**
 * DTO nội bộ, chỉ dùng cho order-service gọi qua Feign.
 * Chỉ chứa những trường order cần: tên, giá, trạng thái.
 */
public record SkuInfoDto(
        String skuCode,
        String productName,
        BigDecimal price,
        String status        // ACTIVE | INACTIVE
) {}
