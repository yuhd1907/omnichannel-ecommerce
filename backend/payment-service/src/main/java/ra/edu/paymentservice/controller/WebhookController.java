package ra.edu.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ra.edu.common.response.ApiResponse;
import ra.edu.paymentservice.service.WebhookService;

import java.util.Map;

/**
 * Webhook từ cổng thanh toán. Public (permitAll trong PaymentSecurityConfig) — xác thực bằng
 * chữ ký HMAC trong header X-Signature, không bằng JWT.
 */
@RestController
@RequestMapping("/api/v1/webhooks/payments")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * Nhận body dạng byte[]: chữ ký được tính trên đúng các byte cổng gửi. Để Spring parse thành
     * object rồi serialize lại thì thứ tự key/khoảng trắng đổi -> chữ ký không bao giờ khớp.
     * X-Signature required=false để thiếu header vẫn vào service và trả 401 INVALID_SIGNATURE
     * đúng format, thay vì 400 MissingRequestHeader.
     */
    @PostMapping("/{method}")
    public ResponseEntity<ApiResponse<Map<String, String>>> receive(
            @PathVariable String method,
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        WebhookService.Result result = webhookService.handle(method, body, signature);
        return ResponseEntity.ok(ApiResponse.success(Map.of("result", result.name())));
    }
}
