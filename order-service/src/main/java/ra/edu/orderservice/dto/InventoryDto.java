package ra.edu.orderservice.dto;

import java.util.UUID;

/**
 * Response trả về trạng thái tồn kho của một SKU.
 */
public record InventoryDto(
        UUID id,
        String skuCode,
        long availableQty,
        long reservedQty,
        long version
) {}
