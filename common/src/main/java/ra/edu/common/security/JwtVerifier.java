package ra.edu.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Dùng chung để VERIFY và PARSE JWT access token.
 * <p>
 * Không chứa logic phát hành token (generateAccessToken, generateRefreshToken)
 * — phần đó thuộc về identity-service.
 * <p>
 * Nhận secret từ biến môi trường JWT_SECRET giống nhau trên mọi service.
 */
public class JwtVerifier {

    private final SecretKey signingKey;

    public JwtVerifier(String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parse và verify token. Ném JwtException nếu không hợp lệ hoặc hết hạn.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Verify không ném exception; trả false nếu token không hợp lệ.
     */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public String getEmail(String token) {
        return parse(token).get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return parse(token).get("roles", List.class);
    }
}
