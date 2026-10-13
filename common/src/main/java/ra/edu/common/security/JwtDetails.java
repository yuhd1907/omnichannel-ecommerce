package ra.edu.common.security;

/**
 * Thông tin phụ lấy từ access token, gắn vào {@code Authentication#getDetails()}.
 * Principal vẫn là userId (String) để không đổi các chỗ dùng {@code @AuthenticationPrincipal String}.
 *
 * @param email claim "email" của token (có thể null với token cũ)
 */
public record JwtDetails(String email) {
}
