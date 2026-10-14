package ra.edu.orderservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.orderservice.dto.InventoryDto;
import ra.edu.orderservice.dto.request.AdjustInventoryRequest;
import ra.edu.orderservice.service.InventoryService;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    /** GET /api/v1/admin/inventory/{skuCode} — xem tồn kho */
    @GetMapping("/{skuCode}")
    public ResponseEntity<ApiResponse<InventoryDto>> getInventory(
            @PathVariable String skuCode
    ) {
        InventoryDto dto = inventoryService.getBySkuCode(skuCode);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * POST /api/v1/admin/inventory/{skuCode}/adjustments — nhập / điều chỉnh kho.
     * Upsert: tạo dòng inventory nếu chưa tồn tại.
     */
    @PostMapping("/{skuCode}/adjustments")
    public ResponseEntity<ApiResponse<InventoryDto>> adjustInventory(
            @PathVariable String skuCode,
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        InventoryDto dto = inventoryService.adjust(skuCode, request);
        return ResponseEntity.ok(
                ApiResponse.of("INVENTORY_ADJUSTED", "Inventory adjusted successfully", dto));
    }
}
