package ra.edu.paymentservice.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ra.edu.paymentservice.entity.Payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Adapter mock dùng cho development và demo.
 * buildCheckoutUrl: trả về URL giả lập để QA có thể nhấn vào.
 * verifySignature:  xác thực HMAC-SHA256 header X-Signature của webhook (WebhookService bước 2).
 */
@Component
public class MockAdapter implements PaymentGatewayAdapter {

    private final String publicBaseUrl;
    private final String webhookSecret;

    public MockAdapter(
            @Value("${app.payment.public-base-url}") String publicBaseUrl,
            @Value("${app.payment.webhook-secret}") String webhookSecret) {
        this.publicBaseUrl = publicBaseUrl;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String method() {
        return "MOCK";
    }

    @Override
    public String buildCheckoutUrl(Payment payment) {
        return publicBaseUrl + "/mock-gateway/checkout/" + payment.getId();
    }

    /**
     * HMAC-SHA256 trên ĐÚNG mảng byte nhận được (không parse/serialize lại: đổi một khoảng trắng
     * là sai chữ ký), ra hex chữ thường, so bằng MessageDigest.isEqual (thời gian so không phụ
     * thuộc vị trí ký tự sai đầu tiên -> không lộ chữ ký qua timing attack như equals()).
     */
    @Override
    public boolean verifySignature(byte[] body, String signature) {
        if (body == null || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(body));   // HexFormat.of() = chữ thường
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.trim().getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
