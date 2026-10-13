package ra.edu.orderservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.common.response.ErrorCode;
import ra.edu.common.response.PageData;
import ra.edu.common.security.JwtDetails;
import ra.edu.orderservice.dto.CreateOrderResult;
import ra.edu.orderservice.dto.OrderDto;
import ra.edu.orderservice.dto.OrderSummaryDto;
import ra.edu.orderservice.dto.request.CreateOrderRequest;
import ra.edu.orderservice.exception.BusinessException;
import ra.edu.orderservice.service.OrderService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Độ dài tối đa của header Idempotency-Key (khớp cột idem_key VARCHAR(100)). */
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    /**
     * POST /api/v1/orders — tạo đơn hàng mới.
     *
     * Header tùy chọn {@code Idempotency-Key}: client sinh 1 UUID cho mỗi lần bấm "Đặt hàng" và gửi
     * lại đúng key đó khi retry (mất mạng, timeout). Cùng user + cùng key + cùng nội dung -> trả lại
     * đơn đã tạo (200, header Idempotent-Replayed: true), không giữ kho lần 2. Cùng key nhưng khác
     * nội dung -> 422 IDEMPOTENCY_KEY_REUSED.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> createOrder(
            @AuthenticationPrincipal String userId,
            Authentication auth,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        // Email lấy từ claim của access token (JwtAuthenticationFilter gắn vào details)
        String email = auth.getDetails() instanceof JwtDetails details ? details.email() : null;
        CreateOrderResult result = orderService.createOrder(
                UUID.fromString(userId), email, request, normalizeKey(idempotencyKey));
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replayed", "true")
                    .body(ApiResponse.of("ORDER_CREATED", "Order already created for this Idempotency-Key", result.order()));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("ORDER_CREATED", "Order created successfully", result.order()));
    }

    static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters",
                    HttpStatus.BAD_REQUEST);
        }
        return key;
    }

    /**
     * GET /api/v1/orders/{id} — chi tiết đơn.
     * Chủ đơn hoặc ADMIN; người khác nhận 404.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrder(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId,
            Authentication auth
    ) {
        boolean isAdmin = isAdmin(auth);
        OrderDto result = orderService.getOrder(id, UUID.fromString(userId), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * GET /api/v1/orders?page=&size= — danh sách đơn của tôi, sort created_at DESC.
     * PageableDefault: page=0, size=20.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageData<OrderSummaryDto>>> listMyOrders(
            @AuthenticationPrincipal String userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageData<OrderSummaryDto> result = orderService.listMyOrders(UUID.fromString(userId), pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * POST /api/v1/orders/{id}/cancel — hủy đơn PENDING.
     * Đổi status và hoàn kho trong cùng transaction.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDto>> cancelOrder(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId,
            Authentication auth
    ) {
        boolean isAdmin = isAdmin(auth);
        OrderDto result = orderService.cancelOrder(id, UUID.fromString(userId), isAdmin);
        return ResponseEntity.ok(ApiResponse.of("ORDER_CANCELLED", "Order cancelled successfully", result));
    }

    /** Kiểm tra token có chứa ROLE_ADMIN không. */
    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
