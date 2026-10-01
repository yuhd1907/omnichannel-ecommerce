package ra.edu.identityservice.security;

import org.springframework.stereotype.Component;
import ra.edu.common.security.JwtAuthenticationFilter;
import ra.edu.common.security.JwtVerifier;

/**
 * Bean Spring cho JWT filter trong identity-service.
 * Kế thừa JwtAuthenticationFilter từ module common, annotated @Component
 * để Spring Boot tự scan và inject vào SecurityFilterChain.
 */
@Component
public class IdentityJwtFilter extends JwtAuthenticationFilter {

    public IdentityJwtFilter(JwtVerifier jwtVerifier) {
        super(jwtVerifier);
    }
}
