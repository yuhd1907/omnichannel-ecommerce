package ra.edu.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import ra.edu.common.response.ErrorCode;
import ra.edu.common.security.JwtVerifier;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private final JwtVerifier jwtVerifier;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthGlobalFilter(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtVerifier = new JwtVerifier(jwtSecret);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (isWhitelisted(request)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing or invalid Authorization header for path: {}", request.getURI().getPath());
            return onError(exchange, "Authentication required");
        }

        String token = authHeader.substring(7).trim();
        if (!jwtVerifier.isValid(token)) {
            log.debug("Invalid or expired JWT token for path: {}", request.getURI().getPath());
            return onError(exchange, "Invalid or expired token");
        }

        // Token hợp lệ: giữ nguyên header Authorization và tiếp tục chain
        return chain.filter(exchange);
    }

    private boolean isWhitelisted(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        HttpMethod method = request.getMethod();

        // 1. Auth endpoints
        if (path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")) {
            return true;
        }

        // 2. Health check
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
            return true;
        }

        // 3. GET /api/v1/products/** (chỉ GET public products, không bao gồm /api/v1/admin/**)
        if (HttpMethod.GET.equals(method)) {
            if (path.equals("/api/v1/products") || pathMatcher.match("/api/v1/products/**", path)) {
                return !path.startsWith("/api/v1/admin/");
            }
        }

        return false;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = String.format(
                "{\"code\":\"%s\",\"message\":\"%s\",\"data\":null,\"errors\":null,\"traceId\":null,\"timestamp\":\"%s\"}",
                ErrorCode.UNAUTHORIZED,
                message,
                Instant.now()
        );
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
