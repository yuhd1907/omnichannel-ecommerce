package ra.edu.productservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import ra.edu.common.response.PageData;
import ra.edu.productservice.dto.ProductSummaryDto;
import ra.edu.productservice.entity.Product;
import ra.edu.productservice.repository.BrandRepository;
import ra.edu.productservice.repository.CategoryRepository;
import ra.edu.productservice.repository.OutboxEventRepository;
import ra.edu.productservice.repository.ProductRepository;
import ra.edu.productservice.repository.ProductSkuRepository;
import ra.edu.productservice.service.impl.ProductServiceImpl;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSearchTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("toPrefixTsQuery: tách từ, hạ chữ thường, thêm :* và nối bằng &")
    void toPrefixTsQuery_buildsPrefixQuery() {
        assertThat(ProductServiceImpl.toPrefixTsQuery("iPhone 15  Pro")).isEqualTo("iphone:* & 15:* & pro:*");
        assertThat(ProductServiceImpl.toPrefixTsQuery("Điện thoại")).isEqualTo("điện:* & thoại:*");
    }

    @Test
    @DisplayName("toPrefixTsQuery: loại bỏ toán tử tsquery người dùng gõ vào -> không lỗi cú pháp")
    void toPrefixTsQuery_stripsOperators() {
        assertThat(ProductServiceImpl.toPrefixTsQuery("iphone | !(samsung) & 'x':*")).isEqualTo("iphone:* & samsung:* & x:*");
        assertThat(ProductServiceImpl.toPrefixTsQuery("  !!! ")).isNull();
        assertThat(ProductServiceImpl.toPrefixTsQuery(null)).isNull();
    }

    @Test
    @DisplayName("toPrefixTsQuery: chữ có dấu dạng tổ hợp (NFD) vẫn là một từ")
    void toPrefixTsQuery_handlesDecomposedVietnamese() {
        String nfd = Normalizer.normalize("giày", Normalizer.Form.NFD);
        assertThat(ProductServiceImpl.toPrefixTsQuery(nfd)).isEqualTo("giày:*");
    }

    @Test
    @DisplayName("searchProducts: giữ thứ tự độ liên quan của id, tính tổng trang từ count")
    void searchProducts_keepsRankOrder() {
        ProductServiceImpl service = new ProductServiceImpl(productRepository, productSkuRepository,
                categoryRepository, brandRepository, outboxEventRepository, JsonMapper.builder().build());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(productRepository.searchIds("iph:*", 2, 0L)).thenReturn(List.of(first, second));
        when(productRepository.countSearch("iph:*")).thenReturn(3L);
        // DB trả về theo thứ tự khác
        when(productRepository.findByIdIn(any())).thenReturn(List.of(
                Product.builder().id(second).name("iPhone 15").build(),
                Product.builder().id(first).name("iPhone 15 Pro Max").build()));

        PageData<ProductSummaryDto> page = service.searchProducts("iph", null, PageRequest.of(0, 2));

        assertThat(page.items()).extracting(ProductSummaryDto::id).containsExactly(first, second);
        assertThat(page.totalElements()).isEqualTo(3L);
        assertThat(page.totalPages()).isEqualTo(2);
        verify(productRepository, never()).findByStatus(any(), any());
    }
}
