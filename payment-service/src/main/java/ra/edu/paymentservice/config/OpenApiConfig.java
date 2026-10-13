package ra.edu.paymentservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Mô tả OpenAPI cho springdoc. Spec ở /v3/api-docs, Swagger UI gom chung ở api-gateway.
 *
 * servers = "/": "Try it out" gọi cùng origin với Swagger UI (tức là qua gateway),
 * không gọi thẳng vào port nội bộ của service.
 * Nút Authorize: dán access token (không cần chữ "Bearer ").
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Payment Service", version = "v1", description = "Thanh toán MOCK, webhook ký HMAC, đối soát hoàn tiền"),
        servers = @Server(url = "/", description = "Qua API Gateway"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
