package ra.edu.productservice.service;

import org.springframework.data.domain.Pageable;
import ra.edu.common.response.PageData;
import ra.edu.productservice.dto.ProductDetailDto;
import ra.edu.productservice.dto.ProductSkuDto;
import ra.edu.productservice.dto.ProductSummaryDto;
import ra.edu.productservice.dto.request.CreateProductRequest;
import ra.edu.productservice.dto.request.CreateSkuRequest;

import ra.edu.productservice.dto.SkuInfoDto;
import ra.edu.productservice.dto.request.UpdateProductRequest;
import ra.edu.productservice.dto.request.UpdateSkuRequest;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductService {

    PageData<ProductSummaryDto> getProducts(UUID categoryId, Pageable pageable);

    ProductDetailDto getProductById(UUID id);

    ProductDetailDto createProduct(CreateProductRequest request);

    ProductSkuDto addSkuToProduct(UUID productId, CreateSkuRequest request);

    ProductDetailDto updateProduct(UUID id, UpdateProductRequest req);

    ProductSkuDto updateSku(String skuCode, UpdateSkuRequest req);

    void deleteProduct(UUID id);

    void deleteSku(String skuCode);

    SkuInfoDto getSkuInfo(String skuCode);

    List<SkuInfoDto> getSkuInfos(Collection<String> skuCodes);
}
