package ra.edu.apigateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config = new RateLimiterConfig(SECRET);
        keyResolver = config.userKeyResolver();
    }

    private String generateToken(String userId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("roles", List.of("USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Có JWT hợp lệ -> resolve theo userId")
    void testResolveWithValidJwt() {
        UUID userId = UUID.randomUUID();
        String token = generateToken(userId.toString());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = keyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("user:" + userId);
    }

    @Test
    @DisplayName("Không có token -> fallback theo IP")
    void testResolveWithNoTokenFallbackToIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = keyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("ip:192.168.1.100");
    }

    @Test
    @DisplayName("Không có token, có X-Forwarded-For -> resolve theo client IP từ header")
    void testResolveWithXForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header("X-Forwarded-For", "203.0.113.195, 70.41.3.18")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = keyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("ip:203.0.113.195");
    }

    @Test
    @DisplayName("Token không hợp lệ -> fallback theo IP")
    void testResolveWithInvalidTokenFallbackToIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = keyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("ip:127.0.0.1");
    }
}
