package ra.edu.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import ra.edu.common.response.ErrorCode;

import java.io.IOException;
import java.time.Instant;

/**
 * Trả về JSON chuẩn ApiResponse 403 khi request không đủ quyền (ví dụ USER truy cập endpoint ADMIN).
 * Dùng chung cho mọi service — không phụ thuộc Jackson để tránh conflict version.
 */
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String json = String.format(
                "{\"code\":\"%s\",\"message\":\"Access denied\",\"data\":null,\"errors\":null,\"traceId\":null,\"timestamp\":\"%s\"}",
                ErrorCode.ACCESS_DENIED,
                Instant.now()
        );
        response.getWriter().write(json);
    }
}
