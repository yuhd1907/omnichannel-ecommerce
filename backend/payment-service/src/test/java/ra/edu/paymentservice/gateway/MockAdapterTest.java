package ra.edu.paymentservice.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MockAdapterTest {

    private static final String SECRET = "test-secret";
    private final MockAdapter adapter = new MockAdapter("http://localhost:8084", SECRET);

    private static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static final byte[] BODY =
            "{\"orderId\":\"o\",\"gatewayTxnId\":\"t\",\"status\":\"SUCCESS\",\"amount\":100}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("Chữ ký đúng (hex chữ thường) -> hợp lệ")
    void validSignature() throws Exception {
        assertThat(adapter.verifySignature(BODY, sign(BODY))).isTrue();
    }

    @Test
    @DisplayName("Đổi 1 byte body (vd thêm khoảng trắng) -> chữ ký không khớp")
    void bodyChanged_invalid() throws Exception {
        String sig = sign(BODY);
        byte[] changed = "{ \"orderId\":\"o\",\"gatewayTxnId\":\"t\",\"status\":\"SUCCESS\",\"amount\":100}"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.verifySignature(changed, sig)).isFalse();
    }

    @Test
    @DisplayName("Thiếu / rỗng / sai chữ ký -> không hợp lệ")
    void missingOrWrong_invalid() {
        assertThat(adapter.verifySignature(BODY, null)).isFalse();
        assertThat(adapter.verifySignature(BODY, "  ")).isFalse();
        assertThat(adapter.verifySignature(BODY, "deadbeef")).isFalse();
    }

    @Test
    @DisplayName("Hex chữ HOA bị từ chối: chuẩn là hex chữ thường")
    void uppercaseHex_rejected() throws Exception {
        assertThat(adapter.verifySignature(BODY, sign(BODY).toUpperCase())).isFalse();
    }
}
