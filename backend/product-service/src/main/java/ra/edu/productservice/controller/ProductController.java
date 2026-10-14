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
    /** Phục vụ ảnh lưu trên đĩa (kể cả ảnh cũ sau khi đã chuyển sang Cloudinary). */
    private final ra.edu.productservice.storage.LocalImageStorage localImageStorage;
    private final ra.edu.productservice.service.ProductAdminQueryService adminQuery;

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

    /**
     * GET /api/v1/products/images/{name} — ảnh sản phẩm đã tải lên (công khai). Tên file do server sinh
     * nên nội dung không đổi -> cache lâu ở trình duyệt.
     */
    @GetMapping("/images/{name}")
    public ResponseEntity<org.springframework.core.io.Resource> getImage(@PathVariable String name) {
        return localImageStorage.load(name)
                .map(path -> {
                    String type = name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
                    return ResponseEntity.ok()
                            .contentType(org.springframework.http.MediaType.parseMediaType(type))
                            .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
                            .<org.springframework.core.io.Resource>body(new org.springframework.core.io.FileSystemResource(path));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** GET /api/v1/products/categories — danh mục (lọc ở trang chủ, chọn khi tạo sản phẩm). */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<java.util.List<ra.edu.productservice.dto.CategorySummaryDto>>> categories() {
        return ResponseEntity.ok(ApiResponse.success(adminQuery.categories()));
    }

    /** GET /api/v1/products/brands — thương hiệu. */
    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<java.util.List<ra.edu.productservice.dto.BrandSummaryDto>>> brands() {
        return ResponseEntity.ok(ApiResponse.success(adminQuery.brands()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailDto>> getProductById(@PathVariable UUID id) {
        ProductDetailDto result = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
