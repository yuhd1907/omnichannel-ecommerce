package ra.edu.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.productservice.dto.ProductDetailDto;
import ra.edu.productservice.dto.ProductSkuDto;
import ra.edu.productservice.dto.request.CreateProductRequest;
import ra.edu.productservice.dto.request.CreateSkuRequest;
import ra.edu.productservice.dto.request.UpdateProductRequest;
import ra.edu.productservice.dto.request.UpdateSkuRequest;
import ra.edu.productservice.service.ProductImageService;
import ra.edu.productservice.service.ProductService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;
    private final ra.edu.productservice.service.ProductAdminQueryService adminQuery;

    /** GET /api/v1/admin/products?q=&status=ACTIVE|INACTIVE&page=&size= — mọi trạng thái, mới nhất trước. */
    @GetMapping
    public ResponseEntity<ApiResponse<ra.edu.common.response.PageData<ra.edu.productservice.dto.ProductSummaryDto>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @org.springframework.data.web.PageableDefault(size = 20, sort = "updatedAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminQuery.list(q, status, pageable)));
    }

    /** GET /api/v1/admin/products/{id} — chi tiết kể cả khi ngừng bán (để sửa / mở bán lại). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailDto>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminQuery.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductDetailDto>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductDetailDto result = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("PRODUCT_CREATED", "Product created successfully", result));
    }

    @PostMapping("/{id}/skus")
    public ResponseEntity<ApiResponse<ProductSkuDto>> addSku(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSkuRequest request
    ) {
        ProductSkuDto result = productService.addSkuToProduct(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("SKU_CREATED", "SKU created successfully", result));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailDto>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductDetailDto result = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.of("PRODUCT_UPDATED", "Product updated successfully", result));
    }

    @PatchMapping("/skus/{skuCode}")
    public ResponseEntity<ApiResponse<ProductSkuDto>> updateSku(
            @PathVariable String skuCode,
            @Valid @RequestBody UpdateSkuRequest request
    ) {
        ProductSkuDto result = productService.updateSku(skuCode, request);
        return ResponseEntity.ok(ApiResponse.of("SKU_UPDATED", "SKU updated successfully", result));
    }

    /**
     * POST /api/v1/admin/products/skus/{skuCode}/image (multipart, field "file"): tải ảnh JPEG/PNG/WebP
     * tối đa 2 MB cho SKU. Trả SKU với imageUrl mới (/api/v1/products/images/...).
     */
    @PostMapping(value = "/skus/{skuCode}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductSkuDto>> uploadSkuImage(
            @PathVariable String skuCode,
            @RequestParam("file") MultipartFile file
    ) {
        ProductSkuDto result = productImageService.uploadSkuImage(skuCode, file);
        return ResponseEntity.ok(ApiResponse.of("SKU_IMAGE_UPLOADED", "Image uploaded", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/skus/{skuCode}")
    public ResponseEntity<Void> deleteSku(@PathVariable String skuCode) {
        productService.deleteSku(skuCode);
        return ResponseEntity.noContent().build();
    }
}
