package ra.edu.identityservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.identityservice.dto.request.AddressRequest;
import ra.edu.identityservice.dto.response.AddressResponse;
import ra.edu.identityservice.dto.response.UserResponse;
import ra.edu.identityservice.service.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal String userId) {
        UserResponse response = userService.getCurrentUser(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.of("SUCCESS", "User profile retrieved successfully", response));
    }

    @GetMapping("/me/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses(@AuthenticationPrincipal String userId) {
        List<AddressResponse> response = userService.getUserAddresses(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.of("SUCCESS", "User addresses retrieved successfully", response));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AddressRequest request
    ) {
        AddressResponse response = userService.createAddress(UUID.fromString(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("SUCCESS", "Address created successfully", response));
    }
}
