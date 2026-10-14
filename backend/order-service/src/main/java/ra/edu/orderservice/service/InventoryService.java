package ra.edu.orderservice.service;

import ra.edu.orderservice.dto.InventoryDto;
import ra.edu.orderservice.dto.request.AdjustInventoryRequest;

public interface InventoryService {

    /** Xem tồn kho theo skuCode. Ném BusinessException(404) nếu chưa có dòng. */
    InventoryDto getBySkuCode(String skuCode);

    /**
     * Upsert: nếu chưa có dòng inventory cho skuCode thì tạo mới với available_qty = 0,
     * sau đó áp dụng delta. Ném BusinessException(400) nếu kết quả available_qty < 0.
     */
    InventoryDto adjust(String skuCode, AdjustInventoryRequest request);
}
