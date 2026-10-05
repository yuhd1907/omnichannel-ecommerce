package ra.edu.orderservice.client.dto;

import java.math.BigDecimal;

/**
 * Mirror của SkuInfoDto từ product-service internal endpoint.
 * Chỉ chứa trường cần thiết — tên sản phẩm, giá, trạng thái.
 */
public record SkuInfo(
        String skuCode,
        String productName,
        BigDecimal price,
        String status   // ACTIVE | INACTIVE
) {}
