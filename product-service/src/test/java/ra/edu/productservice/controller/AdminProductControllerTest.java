package ra.edu.productservice.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = generateToken(UUID.randomUUID().toString(), List.of("ADMIN", "USER"));
        userToken = generateToken(UUID.randomUUID().toString(), List.of("USER"));
    }

    private String generateToken(String userId, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("POST /api/v1/admin/products - Không có token trả về 401 Unauthorized")
    void testCreateProductWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/admin/products - Token USER thường trả về 403 Forbidden")
    void testCreateProductWithUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/products - Admin tạo sản phẩm và SKUs thành công trả về 201")
    void testCreateProductWithAdminRoleSuccess() throws Exception {
        String uniqueSku = "TEST-MACBOOK-M3-" + UUID.randomUUID().toString().substring(0, 6);

        String body = """
                {
                    "name": "MacBook Pro M3",
                    "description": "Laptop đỉnh cao Apple M3",
                    "categoryId": "a0000000-0000-0000-0000-000000000012",
                    "brandId": "b0000000-0000-0000-0000-000000000001",
                    "basePrice": 39990000.00,
                    "status": "ACTIVE",
                    "skus": [
                        {
                            "skuCode": "%s",
                            "attributes": {
                                "color": "Space Black",
                                "ram": "18GB",
                                "storage": "512GB"
                            },
                            "price": 39990000.00,
                            "imageUrl": "https://cdn.example.com/macbook-m3.jpg"
                        }
                    ]
                }
                """.formatted(uniqueSku);

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PRODUCT_CREATED"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("MacBook Pro M3"))
                .andExpect(jsonPath("$.data.skus", hasSize(1)))
                .andExpect(jsonPath("$.data.skus[0].skuCode").value(uniqueSku))
                .andExpect(jsonPath("$.data.skus[0].attributes.color").value("Space Black"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products - Trùng sku_code có sẵn trong DB trả về 409 SKU_EXISTS")
    void testCreateProductDuplicateSkuInDb() throws Exception {
        String body = """
                {
                    "name": "iPhone Trùng SKU",
                    "categoryId": "a0000000-0000-0000-0000-000000000011",
                    "brandId": "b0000000-0000-0000-0000-000000000001",
                    "basePrice": 20000000.00,
                    "skus": [
                        {
                            "skuCode": "IPHONE15PM-NAT-256",
                            "price": 20000000.00
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_EXISTS"))
                .andExpect(jsonPath("$.message").value(containsString("IPHONE15PM-NAT-256")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products - Trùng sku_code trong cùng request trả về 409 SKU_EXISTS")
    void testCreateProductDuplicateSkuInPayload() throws Exception {
        String dupSku = "DUP-SKU-" + UUID.randomUUID().toString().substring(0, 6);

        String body = """
                {
                    "name": "Sản phẩm SKU trùng lặp",
                    "categoryId": "a0000000-0000-0000-0000-000000000011",
                    "brandId": "b0000000-0000-0000-0000-000000000001",
                    "basePrice": 10000000.00,
                    "skus": [
                        { "skuCode": "%s", "price": 10000000.00 },
                        { "skuCode": "%s", "price": 12000000.00 }
                    ]
                }
                """.formatted(dupSku, dupSku);

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products/{id}/skus - Thêm SKU mới cho sản phẩm có sẵn trả về 201")
    void testAddSkuToProductSuccess() throws Exception {
        // iPhone 15 Pro Max: c0000000-0000-0000-0000-000000000001
        UUID productId = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        String newSkuCode = "IPHONE15PM-WHT-1TB-" + UUID.randomUUID().toString().substring(0, 4);

        String body = """
                {
                    "skuCode": "%s",
                    "attributes": {
                        "color": "Titan Trắng",
                        "storage": "1TB"
                    },
                    "price": 42990000.00,
                    "imageUrl": "https://cdn.example.com/iphone15pm-white.jpg"
                }
                """.formatted(newSkuCode);

        mockMvc.perform(post("/api/v1/admin/products/{id}/skus", productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SKU_CREATED"))
                .andExpect(jsonPath("$.data.skuCode").value(newSkuCode))
                .andExpect(jsonPath("$.data.attributes.color").value("Titan Trắng"))
                .andExpect(jsonPath("$.data.price").value(42990000.00));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products/{id}/skus - Thêm SKU bị trùng sku_code trả về 409 SKU_EXISTS")
    void testAddSkuDuplicateSkuCode() throws Exception {
        UUID productId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

        String body = """
                {
                    "skuCode": "IPHONE15PM-BLK-512",
                    "price": 35000000.00
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products/{id}/skus", productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_EXISTS"));
    }
}
