package ra.edu.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Recipient name is required")
        @Size(max = 255, message = "Recipient name cannot exceed 255 characters")
        String recipientName,

        @NotBlank(message = "Phone is required")
        @Size(max = 50, message = "Phone cannot exceed 50 characters")
        String phone,

        @NotBlank(message = "Address line is required")
        @Size(max = 255, message = "Address line cannot exceed 255 characters")
        String addressLine,

        @NotBlank(message = "Ward is required")
        @Size(max = 100, message = "Ward cannot exceed 100 characters")
        String ward,

        @NotBlank(message = "District is required")
        @Size(max = 100, message = "District cannot exceed 100 characters")
        String district,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City cannot exceed 100 characters")
        String city,

        Boolean isDefault
) {
}
