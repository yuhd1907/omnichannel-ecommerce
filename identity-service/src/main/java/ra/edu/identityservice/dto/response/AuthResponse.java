package ra.edu.identityservice.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }

    public static AuthResponse ofAccessToken(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, null, "Bearer", expiresIn);
    }
}
