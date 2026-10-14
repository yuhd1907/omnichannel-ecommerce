package ra.edu.identityservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ra.edu.common.response.ApiResponse;
import ra.edu.identityservice.dto.request.LoginRequest;
import ra.edu.identityservice.dto.request.LogoutRequest;
import ra.edu.identityservice.dto.request.RefreshTokenRequest;
import ra.edu.identityservice.dto.request.RegisterRequest;
import ra.edu.identityservice.dto.response.AuthResponse;
import ra.edu.identityservice.dto.response.UserResponse;
import ra.edu.identityservice.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("SUCCESS", "User registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String deviceInfo = httpRequest.getHeader("X-Device-Info");
        AuthResponse response = authService.login(request, userAgent, deviceInfo);
        return ResponseEntity.ok(ApiResponse.of("SUCCESS", "Login successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.of("SUCCESS", "Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.of("SUCCESS", "Logged out successfully", null));
    }
}
