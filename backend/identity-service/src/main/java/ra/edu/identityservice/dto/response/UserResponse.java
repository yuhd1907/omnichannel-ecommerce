package ra.edu.identityservice.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String status,
        Set<String> roles,
        Instant createdAt
) {
}
