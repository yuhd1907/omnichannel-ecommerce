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
import ra.edu.productservice.entity.OutboxEvent;
import ra.edu.productservice.entity.Product;
import ra.edu.productservice.entity.ProductSku;
import ra.edu.productservice.exception.ResourceNotFoundException;
import ra.edu.productservice.exception.SkuAlreadyExistsException;
import ra.edu.productservice.repository.BrandRepository;
import ra.edu.productservice.repository.CategoryRepository;
import ra.edu.productservice.repository.OutboxEventRepository;
import ra.edu.productservice.repository.ProductRepository;
import ra.edu.productservice.repository.ProductSkuRepository;
import ra.edu.productservice.service.ProductService;
import tools.jackson.databind.json.JsonMapper;

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
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

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

    /** Giới hạn số từ khóa để tsquery không phình to (mỗi từ là một điều kiện AND). */
    static final int MAX_SEARCH_TERMS = 8;

    @Override
    @Transactional(readOnly = true)
    public PageData<ProductSummaryDto> searchProducts(String keyword, UUID categoryId, Pageable pageable) {
        String tsQuery = toPrefixTsQuery(keyword);
        if (tsQuery == null) {
            // Từ khóa chỉ toàn ký tự đặc biệt -> coi như không lọc
            return getProducts(categoryId, pageable);
        }

        int size = pageable.getPageSize();
        long offset = pageable.getOffset();
        List<UUID> ids;
        long total;
        if (categoryId != null) {
            ids = productRepository.searchIdsInCategory(tsQuery, categoryId, size, offset);
            total = productRepository.countSearchInCategory(tsQuery, categoryId);
        } else {
            ids = productRepository.searchIds(tsQuery, size, offset);
            total = productRepository.countSearch(tsQuery);
        }

        // findByIdIn không giữ thứ tự -> sắp lại theo thứ tự độ liên quan của ids
        Map<UUID, Product> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            productRepository.findByIdIn(ids).forEach(p -> byId.put(p.getId(), p));
        }
        List<ProductSummaryDto> content = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProductSummaryDto::from)
                .toList();

        int totalPages = size == 0 ? 1 : (int) ((total + size - 1) / size);
        return new PageData<>(content, pageable.getPageNumber(), size, total, totalPages);
    }

    /**
     * Từ khóa người dùng -> tsquery an toàn: tách theo ký tự không phải chữ/số (nên người dùng
     * không thể chèn toán tử tsquery như ! | ( ) làm lỗi cú pháp), mỗi từ thêm ":*" để khớp tiền
     * tố, nối bằng "&". Ví dụ "iPhone 15 pro" -> "iphone:* & 15:* & pro:*".
     *
     * @return null nếu không còn từ nào hợp lệ.
     */
    public static String toPrefixTsQuery(String keyword) {
        if (keyword == null) {
            return null;
        }
        // NFC: chữ có dấu gõ dạng tổ hợp (e + dấu) gộp lại thành một ký tự \p{L}
        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        List<String> terms = Arrays.stream(normalized.split("[^\\p{L}\\p{N}]+"))
                .filter(t -> !t.isEmpty())
                .map(t -> t.length() > 50 ? t.substring(0, 50) : t)
                .distinct()
                .limit(MAX_SEARCH_TERMS)
                .map(t -> t + ":*")
                .toList();
        return terms.isEmpty() ? null : String.join(" & ", terms);
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
        // Cùng transaction với việc tạo SKU: commit thì chắc chắn có event, rollback thì không.
        savedProduct.getSkus().forEach(sku -> writeSkuCreatedEvent(savedProduct, sku));
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
        writeSkuCreatedEvent(product, savedSku);
        return ProductSkuDto.from(savedSku);
    }

    /**
     * Ghi event PRODUCT_SKU_CREATED vào outbox (Transactional Outbox). OutboxRelay publish lên
     * exchange product.events; order-service nhận và tạo dòng kho available_qty = 0 cho SKU,
     * admin chỉ còn việc nhập số lượng.
     */
    private void writeSkuCreatedEvent(Product product, ProductSku sku) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skuCode", sku.getSkuCode());
        payload.put("productId", product.getId());
        payload.put("productName", product.getName());
        payload.put("price", sku.getPrice());
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Product")
                .aggregateId(product.getId())
                .eventType("PRODUCT_SKU_CREATED")
                .payload(jsonMapper.writeValueAsString(payload))
                .build());
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
