package ra.edu.identityservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import ra.edu.common.security.JwtVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Chỉ chứa logic PHÁT HÀNH token (generateAccessToken, generateRefreshToken).
 * Verify/parse token dùng {@link JwtVerifier} từ module common.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String secret;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secret = secret;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * Bean dùng chung cho filter xác thực trong identity-service.
     * Mọi service khác tự tạo bean JwtVerifier riêng với secret từ môi trường.
     */
    @Bean
    public JwtVerifier jwtVerifier() {
        return new JwtVerifier(secret);
    }

    /**
     * Sinh access token chua userId, email, roles.
     */
    public String generateAccessToken(UUID userId, String email, Collection<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", new ArrayList<>(roles))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Sinh refresh token — chi chua userId, khong chua role/email.
     */
    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
