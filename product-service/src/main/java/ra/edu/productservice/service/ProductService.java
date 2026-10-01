package ra.edu.productservice.service;

import org.springframework.data.domain.Pageable;
import ra.edu.common.response.PageData;
import ra.edu.productservice.dto.ProductDetailDto;
import ra.edu.productservice.dto.ProductSkuDto;
import ra.edu.productservice.dto.ProductSummaryDto;
import ra.edu.productservice.dto.request.CreateProductRequest;
import ra.edu.productservice.dto.request.CreateSkuRequest;

import java.util.UUID;

public interface ProductService {

    PageData<ProductSummaryDto> getProducts(UUID categoryId, Pageable pageable);

    ProductDetailDto getProductById(UUID id);

    ProductDetailDto createProduct(CreateProductRequest request);

    ProductSkuDto addSkuToProduct(UUID productId, CreateSkuRequest request);
}
