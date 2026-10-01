package ra.edu.productservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.productservice.entity.Category;
import ra.edu.productservice.entity.Product;
import ra.edu.productservice.entity.ProductSku;
import ra.edu.productservice.repository.BrandRepository;
import ra.edu.productservice.repository.CategoryRepository;
import ra.edu.productservice.repository.ProductRepository;
import ra.edu.productservice.repository.ProductSkuRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductServiceApplicationTests {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSkuRepository productSkuRepository;

    @Test
    void contextLoads() {
        assertThat(categoryRepository).isNotNull();
        assertThat(brandRepository).isNotNull();
        assertThat(productRepository).isNotNull();
        assertThat(productSkuRepository).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Test đọc dữ liệu Category bao gồm root và sub categories")
    void testCategoriesHierarchy() {
        List<Category> rootCategories = categoryRepository.findByParentIsNull();
        assertThat(rootCategories).hasSize(2);

        Optional<Category> dienTu = categoryRepository.findBySlug("thiet-bi-dien-tu");
        assertThat(dienTu).isPresent();

        List<Category> subCategories = categoryRepository.findByParentId(dienTu.get().getId());
        assertThat(subCategories).hasSize(3);
    }

    @Test
    @Transactional
    @DisplayName("Test đọc Product cùng eager fetch Brand, Category và SKUs")
    void testProductWithDetails() {
        Optional<Product> productOpt = productRepository.findBySlugWithDetails("iphone-15-pro-max");
        assertThat(productOpt).isPresent();

        Product product = productOpt.get();
        assertThat(product.getName()).isEqualTo("iPhone 15 Pro Max");
        assertThat(product.getBrand().getName()).isEqualTo("Apple");
        assertThat(product.getCategory().getName()).isEqualTo("Điện thoại thông minh");
        assertThat(product.getSkus()).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Test đọc ProductSku với cột attributes jsonb map sang Map<String, Object>")
    void testProductSkuJsonbAttributes() {
        Optional<ProductSku> skuOpt = productSkuRepository.findBySkuCode("IPHONE15PM-NAT-256");
        assertThat(skuOpt).isPresent();

        ProductSku sku = skuOpt.get();
        assertThat(sku.getAttributes()).isNotNull();
        assertThat(sku.getAttributes().get("color")).isEqualTo("Titan Tự Nhiên");
        assertThat(sku.getAttributes().get("storage")).isEqualTo("256GB");
    }

    @Test
    @Transactional
    @DisplayName("Test tìm danh sách SKU theo list sku_codes cho Order service")
    void testFindBySkuCodeIn() {
        List<ProductSku> skus = productSkuRepository.findBySkuCodeIn(
                List.of("IPHONE15PM-NAT-256", "S24U-GRY-256", "NIKE-AF1-WHT-42")
        );
        assertThat(skus).hasSize(3);
    }
}
