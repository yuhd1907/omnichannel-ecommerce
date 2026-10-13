package ra.edu.productservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.common.response.PageData;
import ra.edu.productservice.dto.ProductDetailDto;
import ra.edu.productservice.dto.ProductSummaryDto;
import ra.edu.productservice.service.ProductService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * GET /api/v1/products?q=&categoryId=&page=&size= — danh sách sản phẩm ACTIVE.
     * Có q: tìm kiếm toàn văn theo tên/mô tả (không phân biệt dấu, gõ dở vẫn khớp), sắp theo
     * độ liên quan. Không có q: như cũ (lọc theo danh mục nếu có).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageData<ProductSummaryDto>>> getProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String q,
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        PageData<ProductSummaryDto> result = (q != null && !q.isBlank())
                ? productService.searchProducts(q, categoryId, pageable)
                : productService.getProducts(categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailDto>> getProductById(@PathVariable UUID id) {
        ProductDetailDto result = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
