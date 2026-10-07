package ra.edu.productservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.common.response.PageData;
import ra.edu.productservice.dto.ProductDetailDto;
import ra.edu.productservice.dto.ProductSkuDto;
import ra.edu.productservice.dto.ProductSummaryDto;
import ra.edu.productservice.dto.SkuInfoDto;
import ra.edu.productservice.dto.request.CreateProductRequest;
import ra.edu.productservice.dto.request.CreateSkuRequest;
import ra.edu.productservice.dto.request.UpdateProductRequest;
import ra.edu.productservice.dto.request.UpdateSkuRequest;
import ra.edu.productservice.entity.Brand;
import ra.edu.productservice.entity.Category;
import ra.edu.productservice.entity.Product;
import ra.edu.productservice.entity.ProductSku;
import ra.edu.productservice.exception.ResourceNotFoundException;
import ra.edu.productservice.exception.SkuAlreadyExistsException;
import ra.edu.productservice.repository.BrandRepository;
import ra.edu.productservice.repository.CategoryRepository;
import ra.edu.productservice.repository.ProductRepository;
import ra.edu.productservice.repository.ProductSkuRepository;
import ra.edu.productservice.service.ProductService;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public PageData<ProductSummaryDto> getProducts(UUID categoryId, Pageable pageable) {
        Page<Product> page = (categoryId != null)
                ? productRepository.findByCategoryIdAndStatus(categoryId, "ACTIVE", pageable)
                : productRepository.findByStatus("ACTIVE", pageable);

        Page<ProductSummaryDto> dtoPage = page.map(ProductSummaryDto::from);

        return new PageData<>(
                dtoPage.getContent(),
                dtoPage.getNumber(),
                dtoPage.getSize(),
                dtoPage.getTotalElements(),
                dtoPage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailDto getProductById(UUID id) {
        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found with id: " + id));

        return ProductDetailDto.from(product);
    }

    @Override
    @Transactional
    public ProductDetailDto createProduct(CreateProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.brandId()));

        // Kiểm tra trùng skuCode ngay trong payload request
        Set<String> requestSkuCodes = new HashSet<>();
        for (CreateSkuRequest skuReq : request.skus()) {
            if (!requestSkuCodes.add(skuReq.skuCode())) {
                throw new SkuAlreadyExistsException("Duplicate sku_code in request: " + skuReq.skuCode());
            }
        }

        // Kiểm tra trùng skuCode trong DB
        List<ProductSku> existingSkus = productSkuRepository.findBySkuCodeIn(requestSkuCodes);
        if (!existingSkus.isEmpty()) {
            String duplicateCode = existingSkus.get(0).getSkuCode();
            throw new SkuAlreadyExistsException("SKU already exists: " + duplicateCode);
        }

        // Chuẩn hóa slug
        String slug = (request.slug() != null && !request.slug().isBlank())
                ? request.slug().trim().toLowerCase()
                : toSlug(request.name());

        if (productRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        Product product = Product.builder()
                .name(request.name())
                .slug(slug)
                .description(request.description())
                .category(category)
                .brand(brand)
                .basePrice(request.basePrice())
                .status(request.status() != null && !request.status().isBlank() ? request.status() : "ACTIVE")
                .skus(new ArrayList<>())
                .build();

        for (CreateSkuRequest skuReq : request.skus()) {
            ProductSku sku = ProductSku.builder()
                    .product(product)
                    .skuCode(skuReq.skuCode())
                    .attributes(skuReq.attributes())
                    .price(skuReq.price())
                    .imageUrl(skuReq.imageUrl())
                    .build();
            product.getSkus().add(sku);
        }

        Product savedProduct = productRepository.save(product);
        return ProductDetailDto.from(savedProduct);
    }

    @Override
    @Transactional
    public ProductSkuDto addSkuToProduct(UUID productId, CreateSkuRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found with id: " + productId));

        if (productSkuRepository.existsBySkuCode(request.skuCode())) {
            throw new SkuAlreadyExistsException("SKU already exists: " + request.skuCode());
        }

        ProductSku sku = ProductSku.builder()
                .product(product)
                .skuCode(request.skuCode())
                .attributes(request.attributes())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .build();

        ProductSku savedSku = productSkuRepository.save(sku);
        return ProductSkuDto.from(savedSku);
    }

    @Override
    @Transactional
    public ProductDetailDto updateProduct(UUID id, UpdateProductRequest req) {
        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found with id: " + id));

        if (req.name() != null && !req.name().isBlank()) {
            product.setName(req.name().trim());
        }
        if (req.description() != null) {
            product.setDescription(req.description());
        }
        if (req.categoryId() != null) {
            Category category = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + req.categoryId()));
            product.setCategory(category);
        }
        if (req.brandId() != null) {
            Brand brand = brandRepository.findById(req.brandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + req.brandId()));
            product.setBrand(brand);
        }
        if (req.basePrice() != null) {
            product.setBasePrice(req.basePrice());
        }
        if (req.status() != null && !req.status().isBlank()) {
            product.setStatus(req.status().trim().toUpperCase());
        }
        product.setUpdatedAt(Instant.now());

        Product savedProduct = productRepository.save(product);
        return ProductDetailDto.from(savedProduct);
    }

    @Override
    @Transactional
    public ProductSkuDto updateSku(String skuCode, UpdateSkuRequest req) {
        ProductSku sku = productSkuRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new ResourceNotFoundException("SKU_NOT_FOUND", "SKU not found with skuCode: " + skuCode));

        if (req.price() != null) {
            sku.setPrice(req.price());
        }
        if (req.imageUrl() != null) {
            sku.setImageUrl(req.imageUrl());
        }
        if (req.attributes() != null) {
            sku.setAttributes(req.attributes());
        }
        sku.setUpdatedAt(Instant.now());

        ProductSku savedSku = productSkuRepository.save(sku);
        return ProductSkuDto.from(savedSku);
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found with id: " + id));

        product.setStatus("INACTIVE");
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void deleteSku(String skuCode) {
        ProductSku sku = productSkuRepository.findBySkuCodeWithProduct(skuCode)
                .orElseThrow(() -> new ResourceNotFoundException("SKU_NOT_FOUND", "SKU not found with skuCode: " + skuCode));

        Product product = sku.getProduct();
        if (product != null && product.getSkus() != null) {
            product.getSkus().remove(sku);
        }
        productSkuRepository.delete(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public SkuInfoDto getSkuInfo(String skuCode) {
        return productSkuRepository.findBySkuCodeWithProduct(skuCode)
                .map(sku -> new SkuInfoDto(
                        sku.getSkuCode(),
                        sku.getProduct().getName(),
                        sku.getPrice(),
                        sku.getProduct().getStatus()
                ))
                .orElseThrow(() -> new ResourceNotFoundException("SKU_NOT_FOUND", "SKU not found: " + skuCode));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkuInfoDto> getSkuInfos(Collection<String> skuCodes) {
        if (skuCodes == null || skuCodes.isEmpty()) {
            return List.of();
        }
        return productSkuRepository.findBySkuCodeInWithProduct(skuCodes).stream()
                .map(sku -> new SkuInfoDto(
                        sku.getSkuCode(),
                        sku.getProduct().getName(),
                        sku.getPrice(),
                        sku.getProduct().getStatus()
                ))
                .toList();
    }

    private String toSlug(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input.trim().toLowerCase(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[đĐ]", "d")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-");
    }
}
