package ra.edu.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Recipient name is required")
        @Size(max = 255, message = "Recipient name cannot exceed 255 characters")
        String recipientName,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = ra.edu.identityservice.util.PhoneNumbers.VN_MOBILE_REGEX,
                message = "Phone must be a Vietnamese mobile number, e.g. 0912345678")
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
