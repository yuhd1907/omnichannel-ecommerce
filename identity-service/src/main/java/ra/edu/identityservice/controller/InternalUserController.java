package ra.edu.identityservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.identityservice.dto.response.AddressResponse;
import ra.edu.identityservice.entity.Address;
import ra.edu.identityservice.exception.ResourceNotFoundException;
import ra.edu.identityservice.repository.AddressRepository;

import java.util.UUID;

/**
 * Internal endpoint — chỉ dùng cho giao tiếp service-to-service.
 * Không cần auth, Gateway không expose /internal/** ra ngoài.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final AddressRepository addressRepository;

    /**
     * GET /internal/addresses/{addressId}
     * Trả về địa chỉ kèm userId để order-service kiểm tra ownership.
     */
    @GetMapping("/addresses/{addressId}")
    public ResponseEntity<ApiResponse<AddressDetailResponse>> getAddress(
            @PathVariable UUID addressId
    ) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found: " + addressId));

        AddressDetailResponse response = new AddressDetailResponse(
                address.getId(),
                address.getUser().getId(),   // caller dùng để kiểm tra ownership
                address.getRecipientName(),
                address.getPhone(),
                address.getAddressLine(),
                address.getWard(),
                address.getDistrict(),
                address.getCity(),
                address.getIsDefault()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Record nội bộ — có thêm userId để order-service verify ownership. */
    public record AddressDetailResponse(
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
}
