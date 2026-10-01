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
import ra.edu.productservice.service.ProductService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

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
}
