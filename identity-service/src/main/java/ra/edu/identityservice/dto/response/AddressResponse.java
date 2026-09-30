package ra.edu.identityservice.dto.response;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String recipientName,
        String phone,
        String addressLine,
        String ward,
        String district,
        String city,
        Boolean isDefault
) {
}
