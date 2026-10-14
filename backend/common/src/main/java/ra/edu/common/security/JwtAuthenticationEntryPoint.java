package ra.edu.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Instant;

/**
 * Trả về JSON chuẩn ApiError 401 khi request đến endpoint yêu cầu xác thực mà không có token.
 * Dùng chung cho mọi service — không phụ thuộc Jackson để tránh conflict version.
 */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String json = String.format(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"data\":null,\"errors\":null,\"traceId\":null,\"timestamp\":\"%s\"}",
                Instant.now()
        );
        response.getWriter().write(json);
    }
}
