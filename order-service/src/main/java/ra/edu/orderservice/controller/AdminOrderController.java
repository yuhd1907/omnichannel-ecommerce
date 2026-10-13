package ra.edu.orderservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.common.response.PageData;
import ra.edu.orderservice.dto.OrderDto;
import ra.edu.orderservice.dto.OrderSummaryDto;
import ra.edu.orderservice.dto.request.UpdateOrderStatusRequest;
import ra.edu.orderservice.service.OrderService;

import java.util.UUID;

/**
 * Quản lý đơn cho ADMIN: xem toàn bộ đơn và đẩy vòng đời giao hàng
 * CONFIRMED -> SHIPPING -> COMPLETED.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    /** GET /api/v1/admin/orders?status=CONFIRMED&page=0&size=20 */
    @GetMapping
    public ResponseEntity<ApiResponse<PageData<OrderSummaryDto>>> listOrders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.listAllOrders(status, pageable)));
    }

    /** PATCH /api/v1/admin/orders/{id}/status  body: {"status":"SHIPPING"} */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderDto>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        OrderDto dto = orderService.updateStatusByAdmin(id, request.status());
        return ResponseEntity.ok(ApiResponse.of("ORDER_STATUS_UPDATED", "Order status updated", dto));
    }
}
