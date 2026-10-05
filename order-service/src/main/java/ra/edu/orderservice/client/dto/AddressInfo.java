package ra.edu.orderservice.client.dto;

import java.util.UUID;

/**
 * Mirror của InternalUserController.AddressDetailResponse từ identity-service.
 * Có thêm userId để order-service kiểm tra ownership.
 */
public record AddressInfo(
        UUID id,
        UUID userId,
        String recipientName,
        String phone,
        String addressLine,
        String ward,
        String district,
        String city,
        Boolean isDefault
) {}
