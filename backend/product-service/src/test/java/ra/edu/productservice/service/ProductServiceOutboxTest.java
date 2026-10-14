package ra.edu.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ra.edu.productservice.dto.request.CreateProductRequest;
import ra.edu.productservice.dto.request.CreateSkuRequest;
import ra.edu.productservice.entity.Brand;
import ra.edu.productservice.entity.Category;
import ra.edu.productservice.entity.OutboxEvent;
import ra.edu.productservice.entity.Product;
import ra.edu.productservice.entity.ProductSku;
import ra.edu.productservice.repository.BrandRepository;
import ra.edu.productservice.repository.CategoryRepository;
import ra.edu.productservice.repository.OutboxEventRepository;
import ra.edu.productservice.repository.ProductRepository;
import ra.edu.productservice.repository.ProductSkuRepository;
import ra.edu.productservice.service.impl.ProductServiceImpl;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tạo SKU phải ghi event PRODUCT_SKU_CREATED vào outbox trong cùng transaction. */
@ExtendWith(MockitoExtension.class)
class ProductServiceOutboxTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private OutboxEventRepository outboxEventRepository;

    private ProductServiceImpl service;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(productRepository, productSkuRepository, categoryRepository,
                brandRepository, outboxEventRepository, jsonMapper);
    }

    @Test
    @DisplayName("createProduct với 2 SKU -> 2 event PRODUCT_SKU_CREATED, payload có skuCode")
    void createProduct_writesOneEventPerSku() {
        UUID categoryId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(Category.builder().id(categoryId).name("C").build()));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(Brand.builder().id(brandId).name("B").build()));
        when(productSkuRepository.findBySkuCodeIn(any())).thenReturn(List.of());
        when(productRepository.existsBySlug(any())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(productId);
            return p;
        });

        CreateProductRequest request = new CreateProductRequest("Ao thun", null, null, categoryId, brandId,
                new BigDecimal("100000"), null, List.of(
                new CreateSkuRequest("TEE-WHT-M", null, new BigDecimal("100000"), null),
                new CreateSkuRequest("TEE-WHT-L", null, new BigDecimal("110000"), null)));

        service.createProduct(request);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(captor.capture());
        List<OutboxEvent> events = captor.getAllValues();
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getEventType()).isEqualTo("PRODUCT_SKU_CREATED");
            assertThat(e.getAggregateId()).isEqualTo(productId);
            assertThat(e.getStatus()).isEqualTo("PENDING");
        });
        assertThat(jsonMapper.readTree(events.get(0).getPayload()).get("skuCode").asString()).isEqualTo("TEE-WHT-M");
        assertThat(jsonMapper.readTree(events.get(1).getPayload()).get("skuCode").asString()).isEqualTo("TEE-WHT-L");
    }

    @Test
    @DisplayName("addSkuToProduct -> 1 event PRODUCT_SKU_CREATED")
    void addSku_writesEvent() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).name("Ao thun").build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productSkuRepository.existsBySkuCode("TEE-BLK-M")).thenReturn(false);
        when(productSkuRepository.save(any(ProductSku.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addSkuToProduct(productId, new CreateSkuRequest("TEE-BLK-M", null, new BigDecimal("100000"), null));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"skuCode\":\"TEE-BLK-M\"");
    }
}
