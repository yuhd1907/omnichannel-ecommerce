package ra.edu.paymentservice.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ra.edu.paymentservice.entity.Payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Adapter mock dùng cho development và demo.
 * buildCheckoutUrl: trả về URL giả lập để QA có thể nhấn vào.
 * verifySignature:  xác thực HMAC-SHA256 (cùng logic với WebhookController).
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

    @Override
    public boolean verifySignature(byte[] body, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(body);
            String expected = HexFormat.of().formatHex(computed);
            return expected.equalsIgnoreCase(signature.trim());
        } catch (Exception e) {
            return false;
        }
    }
}
