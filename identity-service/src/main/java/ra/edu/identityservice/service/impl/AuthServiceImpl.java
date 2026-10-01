package ra.edu.identityservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.identityservice.dto.request.LoginRequest;
import ra.edu.identityservice.dto.request.LogoutRequest;
import ra.edu.identityservice.dto.request.RefreshTokenRequest;
import ra.edu.identityservice.dto.request.RegisterRequest;
import ra.edu.identityservice.dto.response.AuthResponse;
import ra.edu.identityservice.dto.response.UserResponse;
import ra.edu.identityservice.entity.RefreshToken;
import ra.edu.identityservice.entity.Role;
import ra.edu.identityservice.entity.User;
import ra.edu.identityservice.exception.EmailAlreadyExistsException;
import ra.edu.identityservice.exception.InvalidCredentialsException;
import ra.edu.identityservice.exception.InvalidRefreshTokenException;
import ra.edu.identityservice.repository.RefreshTokenRepository;
import ra.edu.identityservice.repository.RoleRepository;
import ra.edu.identityservice.repository.UserRepository;
import ra.edu.identityservice.security.JwtService;
import ra.edu.identityservice.service.AuthService;
import ra.edu.identityservice.util.TokenHashUtil;
import ra.edu.common.security.JwtVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtVerifier jwtVerifier;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Email already exists: " + normalizedEmail);
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("USER").build()));

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phone(request.phone() != null ? request.phone().trim() : null)
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);
        user.addRole(userRole);
        user = userRepository.save(user);

        log.info("User registered successfully with ID: {}", user.getId());
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent, String deviceInfo) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailWithRoles(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new InvalidCredentialsException("Account is not active");
        }

        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roleNames);
        String rawRefreshToken = jwtService.generateRefreshToken(user.getId());
        String tokenHash = TokenHashUtil.hash(rawRefreshToken);
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshTokenExpiration());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .userAgent(userAgent)
                .deviceInfo(deviceInfo)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("User logged in successfully with ID: {}", user.getId());

        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.getAccessTokenExpiration() / 1000);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String rawRefreshToken = request.refreshToken();
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token cannot be blank");
        }

        if (!jwtVerifier.isValid(rawRefreshToken)) {
            throw new InvalidRefreshTokenException("Invalid refresh token signature or expired");
        }

        String tokenHash = TokenHashUtil.hash(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashWithUserAndRoles(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        User user = refreshToken.getUser();
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new InvalidRefreshTokenException("User account is not active");
        }

        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roleNames);
        log.info("Access token refreshed for user ID: {}", user.getId());

        return AuthResponse.of(newAccessToken, rawRefreshToken, jwtService.getAccessTokenExpiration() / 1000);
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        String rawRefreshToken = request.refreshToken();
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = TokenHashUtil.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(refreshToken -> {
            if (!Boolean.TRUE.equals(refreshToken.getRevoked())) {
                refreshToken.setRevoked(true);
                refreshToken.setRevokedAt(Instant.now());
                refreshTokenRepository.save(refreshToken);
                log.info("Refresh token revoked for user ID: {}", refreshToken.getUser().getId());
            }
        });
    }

    private UserResponse mapToUserResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus(),
                roleNames,
                user.getCreatedAt()
        );
    }
}
