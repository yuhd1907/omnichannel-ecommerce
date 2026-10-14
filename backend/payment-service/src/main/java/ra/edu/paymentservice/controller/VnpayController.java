package ra.edu.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ra.edu.common.response.ApiResponse;
import ra.edu.paymentservice.service.VnpayCallbackService;

import java.util.Map;

/**
 * Endpoint VNPAY gọi về. Public (permitAll ở PaymentSecurityConfig, whitelist ở gateway) —
 * xác thực bằng chữ ký vnp_SecureHash, không bằng JWT.
 *
 * Khai báo trên cổng VNPAY sandbox:
 *  - IPN URL:    https://<domain>/api/v1/payments/vnpay/ipn
 *  - Return URL: biến VNPAY_RETURN_URL (https://<domain>/api/v1/payments/vnpay/return)
 */
@RestController
@RequestMapping("/api/v1/payments/vnpay")
@RequiredArgsConstructor
public class VnpayController {

    private final VnpayCallbackService callbackService;

    /** IPN: VNPAY gọi server-to-server. Luôn trả 200 với RspCode theo đặc tả VNPAY. */
    @GetMapping("/ipn")
    public ResponseEntity<VnpayCallbackService.IpnResponse> ipn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(callbackService.handleIpn(params));
    }

    /**
     * Return URL: trình duyệt người mua quay về sau khi thanh toán. Hệ thống không có giao diện
     * nên trả JSON {orderId, result}; trạng thái đơn xem bằng GET /api/v1/orders/{orderId}.
     */
    @GetMapping("/return")
    public ResponseEntity<ApiResponse<VnpayCallbackService.ReturnResult>> returnUrl(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(ApiResponse.success(callbackService.handleReturn(params)));
    }
}
