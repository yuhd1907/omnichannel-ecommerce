package ra.edu.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import ra.edu.common.security.JwtVerifier;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.UUID;

@Configuration
public class RateLimiterConfig {

    private final JwtVerifier jwtVerifier;

    public RateLimiterConfig(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtVerifier = new JwtVerifier(jwtSecret);
    }

    /**
     * KeyResolver xác định khóa rate limiting:
     * - Nếu có JWT hợp lệ: dùng userId ("user:<id>").
     * - Nếu không có JWT hoặc token không hợp lệ: dùng IP của client ("ip:<ip>").
     *
     * Lý do thiết kế: Giới hạn theo userId công bằng hơn IP đối với người dùng đã xác thực,
     * tránh việc nhiều người dùng cùng mạng NAT bị ảnh hưởng lẫn nhau.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (jwtVerifier.isValid(token)) {
                    try {
                        UUID userId = jwtVerifier.getUserId(token);
                        if (userId != null) {
                            return Mono.just("user:" + userId);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            // Fallback theo client IP
            String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String clientIp = xForwardedFor.split(",")[0].trim();
                return Mono.just("ip:" + clientIp);
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String ip = (remoteAddress != null && remoteAddress.getAddress() != null)
                    ? remoteAddress.getAddress().getHostAddress()
                    : "anonymous";
            return Mono.just("ip:" + ip);
        };
    }
}
