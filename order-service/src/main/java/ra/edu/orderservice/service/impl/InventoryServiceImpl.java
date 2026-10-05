package ra.edu.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.orderservice.dto.InventoryDto;
import ra.edu.orderservice.dto.request.AdjustInventoryRequest;
import ra.edu.orderservice.entity.Inventory;
import ra.edu.orderservice.exception.BusinessException;
import ra.edu.orderservice.repository.InventoryRepository;
import ra.edu.orderservice.service.InventoryService;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    @Override
    @Transactional(readOnly = true)
    public InventoryDto getBySkuCode(String skuCode) {
        Inventory inv = inventoryRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new BusinessException(
                        "INVENTORY_NOT_FOUND",
                        "Inventory not found for sku: " + skuCode,
                        HttpStatus.NOT_FOUND));
        return toDto(inv);
    }

    @Override
    @Transactional
    public InventoryDto adjust(String skuCode, AdjustInventoryRequest request) {
        // Upsert: tạo dòng mới nếu chưa có
        Inventory inv = inventoryRepository.findBySkuCode(skuCode)
                .orElseGet(() -> {
                    log.info("No inventory row for sku '{}', creating with available_qty=0", skuCode);
                    return inventoryRepository.save(
                            Inventory.builder().skuCode(skuCode).build());
                });

        long newAvailable = inv.getAvailableQty() + request.delta();
        if (newAvailable < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_INVENTORY",
                    "Adjustment would make available_qty negative (current=%d, delta=%d)"
                            .formatted(inv.getAvailableQty(), request.delta()),
                    HttpStatus.BAD_REQUEST);
        }

        inv.setAvailableQty(newAvailable);
        inv.setVersion(inv.getVersion() + 1);
        Inventory saved = inventoryRepository.save(inv);

        log.info("Inventory adjusted: sku={}, delta={}, newAvailable={}, reason={}",
                skuCode, request.delta(), newAvailable, request.reason());

        return toDto(saved);
    }

    private InventoryDto toDto(Inventory inv) {
        return new InventoryDto(
                inv.getId(),
                inv.getSkuCode(),
                inv.getAvailableQty(),
                inv.getReservedQty(),
                inv.getVersion());
    }
}
