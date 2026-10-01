package ra.edu.productservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ra.edu.common.security.JwtAuthenticationEntryPoint;
import ra.edu.common.security.JwtAuthenticationFilter;
import ra.edu.common.security.JwtVerifier;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ProductSecurityConfig {

    @Bean
    public JwtVerifier jwtVerifier(@Value("${jwt.secret}") String secret) {
        return new JwtVerifier(secret);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        return new JwtAuthenticationFilter(jwtVerifier);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(new JwtAuthenticationEntryPoint())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/products/**").permitAll()
                        // /api/v1/admin/** chỉ ADMIN, kiểm tra bằng @PreAuthorize("hasRole('ADMIN')")
                        // hoặc khai báo trực tiếp ở đây:
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
