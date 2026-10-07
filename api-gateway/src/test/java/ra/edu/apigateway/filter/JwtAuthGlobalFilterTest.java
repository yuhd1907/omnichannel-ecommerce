package ra.edu.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthGlobalFilterTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthGlobalFilter(SECRET);
    }

    private String generateToken(String userId, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Filter có order là -100")
    void testOrder() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    @Test
    @DisplayName("Whitelist: POST /api/v1/auth/login không token -> cho qua")
    void testWhitelistLogin() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean filterChained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(filterChained.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Whitelist: GET /api/v1/products/** không token -> cho qua")
    void testWhitelistGetProducts() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products/some-product-id").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean filterChained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(filterChained.get()).isTrue();
    }

    @Test
    @DisplayName("Whitelist: GET /actuator/health không token -> cho qua")
    void testWhitelistActuatorHealth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean filterChained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(filterChained.get()).isTrue();
    }

    @Test
    @DisplayName("Protected: GET /api/v1/orders không có token -> trả về 401 UNAUTHORIZED")
    void testProtectedOrdersWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean filterChained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(filterChained.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Protected: GET /api/v1/orders có token không hợp lệ -> trả về 401")
    void testProtectedOrdersWithInvalidToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean filterChained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(filterChained.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Protected: GET /api/v1/orders có token hợp lệ -> cho qua")
    void testProtectedOrdersWithValidToken() {
        String token = generateToken(UUID.randomUUID().toString(), List.of("USER"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean filterChained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(filterChained.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
