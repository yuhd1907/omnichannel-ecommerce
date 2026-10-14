package ra.edu.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filter JWT dùng chung cho mọi service (product, order, payment, notification...).
 * <p>
 * Đọc header {@code Authorization: Bearer <token>}, verify bằng {@link JwtVerifier},
 * và nạp authentication vào {@link SecurityContextHolder}.
 * <p>
 * Mỗi service tự tạo bean {@code JwtVerifier} với đúng secret từ môi trường,
 * sau đó inject vào filter này.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // Parse + verify chữ ký MỘT lần rồi đọc mọi claim từ kết quả
        // (trước đây isValid/getUserId/getRoles mỗi hàm tự parse lại -> 3 lần HMAC mỗi request).
        String userId;
        List<SimpleGrantedAuthority> authorities;
        String email;
        try {
            Claims claims = jwtVerifier.parse(token);
            userId = UUID.fromString(claims.getSubject()).toString();
            List<?> roles = claims.get("roles", List.class);
            if (roles == null) {
                // Refresh token không có claim roles -> không được dùng thay access token
                throw new IllegalArgumentException("Not an access token");
            }
            authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            email = claims.get("email", String.class);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // Sai chữ ký / hết hạn / subject không phải UUID / là refresh token -> coi như chưa đăng nhập
            filterChain.doFilter(request, response);
            return;
        }

        // Token hợp lệ — nạp authentication vào SecurityContext
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authentication.setDetails(new JwtDetails(email));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
