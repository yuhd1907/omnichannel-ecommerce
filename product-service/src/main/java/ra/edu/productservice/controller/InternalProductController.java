package ra.edu.productservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.productservice.dto.SkuInfoDto;
import ra.edu.productservice.exception.ResourceNotFoundException;
import ra.edu.productservice.repository.ProductSkuRepository;

/**
 * Internal endpoint — chỉ dùng cho giao tiếp service-to-service.
 * Không cần auth vì Gateway không expose path /internal/** ra ngoài.
 * Nếu muốn chặt hơn: dùng shared secret header hoặc mTLS (Ngày 10).
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductSkuRepository productSkuRepository;

    /** GET /internal/skus/{skuCode} — lấy thông tin SKU cho order-service */
    @GetMapping("/skus/{skuCode}")
    public ResponseEntity<ApiResponse<SkuInfoDto>> getSkuInfo(@PathVariable String skuCode) {
        return productSkuRepository.findBySkuCodeWithProduct(skuCode)
                .map(sku -> {
                    SkuInfoDto dto = new SkuInfoDto(
                            sku.getSkuCode(),
                            sku.getProduct().getName(),
                            sku.getPrice(),
                            sku.getProduct().getStatus()
                    );
                    return ResponseEntity.ok(ApiResponse.success(dto));
                })
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SKU_NOT_FOUND", "SKU not found: " + skuCode));
    }
}
