package ra.edu.identityservice.service;

import ra.edu.identityservice.dto.request.LoginRequest;
import ra.edu.identityservice.dto.request.LogoutRequest;
import ra.edu.identityservice.dto.request.RefreshTokenRequest;
import ra.edu.identityservice.dto.request.RegisterRequest;
import ra.edu.identityservice.dto.response.AuthResponse;
import ra.edu.identityservice.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request, String userAgent, String deviceInfo);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);
}
