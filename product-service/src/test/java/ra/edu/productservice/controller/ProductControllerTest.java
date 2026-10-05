package ra.edu.productservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/products - Public API, phân trang trả về chuẩn PageData trong ApiResponse")
    void testGetProductsPagination() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("page", "0")
                        .param("size", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.data.totalPages", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items", hasSize(5)))
                .andExpect(jsonPath("$.data.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].category.name").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].brand.name").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/products?categoryId=... - Lọc sản phẩm theo categoryId")
    void testGetProductsByCategory() throws Exception {
        // Category Điện thoại thông minh (a0000000-0000-0000-0000-000000000011) có 4 sản phẩm
        String phoneCategoryId = "a0000000-0000-0000-0000-000000000011";

        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", phoneCategoryId)
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items", hasSize(4)))
                .andExpect(jsonPath("$.data.items[*].category.id", everyItem(is(phoneCategoryId))));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Trả chi tiết sản phẩm kèm danh sách SKU")
    void testGetProductByIdSuccess() throws Exception {
        // iPhone 15 Pro Max: c0000000-0000-0000-0000-000000000001
        String productId = "c0000000-0000-0000-0000-000000000001";

        mockMvc.perform(get("/api/v1/products/{id}", productId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.name").value("iPhone 15 Pro Max"))
                .andExpect(jsonPath("$.data.slug").value("iphone-15-pro-max"))
                .andExpect(jsonPath("$.data.brand.name").value("Apple"))
                .andExpect(jsonPath("$.data.category.name").value("Điện thoại thông minh"))
                .andExpect(jsonPath("$.data.skus", hasSize(2)))
                .andExpect(jsonPath("$.data.skus[0].skuCode").isNotEmpty())
                .andExpect(jsonPath("$.data.skus[0].price").isNotEmpty())
                .andExpect(jsonPath("$.data.skus[0].attributes.color").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Không tìm thấy trả về 404 PRODUCT_NOT_FOUND")
    void testGetProductByIdNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/products/{id}", nonExistentId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString(nonExistentId.toString())));
    }
}
