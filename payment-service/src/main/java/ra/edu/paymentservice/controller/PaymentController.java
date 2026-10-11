package ra.edu.paymentservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.paymentservice.dto.CheckoutRequest;
import ra.edu.paymentservice.dto.PaymentView;
import ra.edu.paymentservice.service.PaymentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/v1/orders/{orderId}/payments
     * Chỉ chủ đơn được gọi.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentView>> checkout(
            @PathVariable UUID orderId,
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        PaymentView view = paymentService.checkout(orderId, userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(view));
    }

    /**
     * GET /api/v1/orders/{orderId}/payments
     * Chủ đơn hoặc ADMIN.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentView>> getPayment(
            @PathVariable UUID orderId,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        PaymentView view = paymentService.getPayment(orderId, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(view));
    }
}
