package ra.edu.identityservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.identityservice.dto.request.LoginRequest;
import ra.edu.identityservice.dto.request.RegisterRequest;
import ra.edu.identityservice.dto.response.AuthResponse;
import ra.edu.identityservice.dto.response.UserResponse;
import ra.edu.identityservice.entity.RefreshToken;
import ra.edu.identityservice.repository.RefreshTokenRepository;
import ra.edu.identityservice.repository.UserRepository;
import ra.edu.identityservice.service.AuthService;
import ra.edu.identityservice.util.TokenHashUtil;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String testEmail;

    @BeforeEach
    void setUp() {
        testEmail = "user_" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Thanh cong tra 201, role USER, ma hoa mat khau")
    void register_Success() throws Exception {
        String body = """
                {
                    "email": "%s",
                    "password": "Password123!",
                    "fullName": "Nguyen Van A",
                    "phone": "0987654321"
                }
                """.formatted(testEmail);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value(testEmail))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));

        assertThat(userRepository.findByEmail(testEmail)).isPresent();
        assertThat(userRepository.findByEmail(testEmail).get().getPasswordHash()).isNotEqualTo("Password123!");
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Email trung tra 409 EMAIL_EXISTS")
    void register_DuplicateEmail_Returns409() throws Exception {
        authService.register(new RegisterRequest(testEmail, "Password123!", "Nguyen Van A", "0987654321"));

        String body = """
                {
                    "email": "%s",
                    "password": "Password123!",
                    "fullName": "Nguyen Van B",
                    "phone": "0987654322"
                }
                """.formatted(testEmail);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Validation failed tra 400 VALIDATION_FAILED")
    void register_InvalidInput_Returns400() throws Exception {
        String body = """
                {
                    "email": "invalid-email",
                    "password": "123",
                    "fullName": ""
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Dung thong tin tra 200, cap token, luu token_hash vao DB")
    void login_Success() throws Exception {
        authService.register(new RegisterRequest(testEmail, "Password123!", "Nguyen Van A", "0987654321"));

        String body = """
                {
                    "email": "%s",
                    "password": "Password123!"
                }
                """.formatted(testEmail);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit-Test-Agent")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Sai mat khau tra 401 INVALID_CREDENTIALS")
    void login_WrongPassword_Returns401() throws Exception {
        authService.register(new RegisterRequest(testEmail, "Password123!", "Nguyen Van A", "0987654321"));

        String body = """
                {
                    "email": "%s",
                    "password": "WrongPassword!"
                }
                """.formatted(testEmail);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Email khong ton tai tra 401 INVALID_CREDENTIALS")
    void login_UnknownEmail_Returns401() throws Exception {
        String body = """
                {
                    "email": "nonexistent@example.com",
                    "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - Refresh token hop le cap access token moi tra 200")
    void refresh_Success() throws Exception {
        authService.register(new RegisterRequest(testEmail, "Password123!", "Nguyen Van A", "0987654321"));
        AuthResponse loginRes = authService.login(new LoginRequest(testEmail, "Password123!"), "Test-Agent", "Test-Device");

        String body = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(loginRes.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - Refresh token khong hop le tra 401 INVALID_REFRESH_TOKEN")
    void refresh_InvalidToken_Returns401() throws Exception {
        String body = """
                {
                    "refreshToken": "invalid.token.string"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - Thu hoi refresh token, dat revoked=true va ghi revoked_at")
    void logout_Success() throws Exception {
        authService.register(new RegisterRequest(testEmail, "Password123!", "Nguyen Van A", "0987654321"));
        AuthResponse loginRes = authService.login(new LoginRequest(testEmail, "Password123!"), "Test-Agent", "Test-Device");

        String tokenHash = TokenHashUtil.hash(loginRes.refreshToken());
        RefreshToken tokenBefore = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(tokenBefore.getRevoked()).isFalse();

        String body = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(loginRes.refreshToken());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        RefreshToken tokenAfter = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(tokenAfter.getRevoked()).isTrue();
        assertThat(tokenAfter.getRevokedAt()).isNotNull();

        // Sau khi logout, refresh bang token nay phai bi 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
