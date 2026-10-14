package ra.edu.productservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Mô tả OpenAPI cho springdoc. Spec ở /v3/api-docs (nội bộ, không qua gateway); bản xuất nằm ở docs/api/*.openapi.json (scripts/export-openapi.sh).
 *
 * servers = "/": spec mô tả API theo đường dẫn công khai qua gateway (/api/v1/...),
 * không phải port nội bộ của service. Security scheme bearer JWT.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Product Catalog Service", version = "v1", description = "Danh mục sản phẩm, SKU, giá (public GET, admin CRUD)"),
        servers = @Server(url = "/", description = "Qua API Gateway"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
