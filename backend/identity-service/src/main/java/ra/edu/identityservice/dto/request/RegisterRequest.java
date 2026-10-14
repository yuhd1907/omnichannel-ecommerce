package ra.edu.identityservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        // BCrypt chi bam 72 byte dau: mat khau dai hon se trung voi moi chuoi co cung 72 ky tu dau
        @Size(min = 6, max = 72, message = "Password must be 6-72 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 255, message = "Full name must be 2-255 characters")
        String fullName,

        // Tuy chon; co thi phai la so di dong VN (luu dang 0xxxxxxxxx, unique)
        @Pattern(regexp = "^$|" + ra.edu.identityservice.util.PhoneNumbers.VN_MOBILE_REGEX,
                message = "Phone must be a Vietnamese mobile number, e.g. 0912345678")
        String phone
) {
}
