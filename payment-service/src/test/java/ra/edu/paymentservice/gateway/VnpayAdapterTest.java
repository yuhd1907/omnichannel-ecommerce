package ra.edu.paymentservice.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ra.edu.common.response.ErrorCode;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnpayAdapterTest {

    private static final String SECRET = "TESTSECRETKEY0123456789ABCDEFGHIJ";
    private static final String RETURN_URL = "http://localhost:8080/api/v1/payments/vnpay/return";
    // 2026-10-13 10:00:00 giờ Việt Nam
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T03:00:00Z"), ZoneId.of("UTC"));

    private final VnpayAdapter adapter = new VnpayAdapter(
            "TESTTMN1", SECRET, "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", RETURN_URL, CLOCK);

    private static Payment payment() {
        Payment p = new Payment();
        p.setId(UUID.fromString("0f8e2b6a-1c2d-4e5f-9a8b-7c6d5e4f3a2b"));
        p.setOrderId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        p.setAmount(new BigDecimal("240000.00"));
        return p;
    }

    /** Tách query của URL thành map (đã URL-decode) — như Spring đưa vào @RequestParam Map. */
    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private static String hmac512(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("checkoutUrl: đủ tham số v2.1.0, số tiền x100, giờ Việt Nam, hết hạn sau 15 phút")
    void buildCheckoutUrl_hasRequiredParams() {
        Map<String, String> p = queryParams(adapter.buildCheckoutUrl(payment(), "203.0.113.7"));

        assertThat(p).containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_TmnCode", "TESTTMN1")
                .containsEntry("vnp_Amount", "24000000")
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_ReturnUrl", RETURN_URL)
                .containsEntry("vnp_IpAddr", "203.0.113.7")
                .containsEntry("vnp_CreateDate", "20261013100000")
                .containsEntry("vnp_ExpireDate", "20261013101500")
                .containsEntry("vnp_TxnRef", "0f8e2b6a1c2d4e5f9a8b7c6d5e4f3a2b20261013100000");
        assertThat(p.get("vnp_SecureHash")).hasSize(128);
    }

    @Test
    @DisplayName("Chữ ký = HMAC-SHA512 trên chuỗi query sắp theo tên, giá trị URL-encode (khoảng trắng = +)")
    void signature_matchesVnpaySpec() throws Exception {
        String url = adapter.buildCheckoutUrl(payment(), "203.0.113.7");
        String raw = URI.create(url).getRawQuery();
        String signed = raw.substring(0, raw.indexOf("&vnp_SecureHash="));

        assertThat(signed).startsWith("vnp_Amount=24000000&vnp_Command=pay&vnp_CreateDate=");
        assertThat(signed).contains("vnp_OrderInfo=Thanh+toan+don+hang+11111111-2222-3333-4444-555555555555");
        assertThat(signed).contains("vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn");
        assertThat(queryParams(url).get("vnp_SecureHash")).isEqualTo(hmac512(signed));
    }

    @Test
    @DisplayName("verifyCallback: tham số VNPAY ký đúng -> true; sửa 1 giá trị -> false")
    void verifyCallback_detectsTampering() throws Exception {
        Map<String, String> cb = new java.util.TreeMap<>();
        cb.put("vnp_Amount", "24000000");
        cb.put("vnp_BankCode", "NCB");
        cb.put("vnp_OrderInfo", "Thanh toan don hang 1");
        cb.put("vnp_ResponseCode", "00");
        cb.put("vnp_TmnCode", "TESTTMN1");
        cb.put("vnp_TransactionNo", "14123456");
        cb.put("vnp_TransactionStatus", "00");
        cb.put("vnp_TxnRef", "0f8e2b6a1c2d4e5f9a8b7c6d5e4f3a2b20261013100000");
        String data = "vnp_Amount=24000000&vnp_BankCode=NCB&vnp_OrderInfo=Thanh+toan+don+hang+1&vnp_ResponseCode=00"
                + "&vnp_TmnCode=TESTTMN1&vnp_TransactionNo=14123456&vnp_TransactionStatus=00"
                + "&vnp_TxnRef=0f8e2b6a1c2d4e5f9a8b7c6d5e4f3a2b20261013100000";
        cb.put("vnp_SecureHashType", "HmacSHA512");
        cb.put("vnp_SecureHash", hmac512(data).toUpperCase());   // VNPAY có thể gửi chữ hoa

        assertThat(adapter.verifyCallback(cb)).isTrue();

        cb.put("vnp_Amount", "100");
        assertThat(adapter.verifyCallback(cb)).isFalse();
        cb.remove("vnp_SecureHash");
        assertThat(adapter.verifyCallback(cb)).isFalse();
    }

    @Test
    @DisplayName("parseCallback: suy ra paymentId từ vnp_TxnRef, số tiền chia 100, 00/00 mới là thành công")
    void parseCallback_readsResult() {
        Map<String, String> cb = Map.of(
                "vnp_TxnRef", "0f8e2b6a1c2d4e5f9a8b7c6d5e4f3a2b20261013100000",
                "vnp_Amount", "24000000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "14123456");

        VnpayAdapter.CallbackResult r = adapter.parseCallback(cb);

        assertThat(r.paymentId()).isEqualTo(payment().getId());
        assertThat(r.amount()).isEqualByComparingTo("240000");
        assertThat(r.success()).isTrue();

        Map<String, String> cancelled = new java.util.HashMap<>(cb);
        cancelled.put("vnp_ResponseCode", "24");   // khách hủy giao dịch
        cancelled.put("vnp_TransactionStatus", "02");
        assertThat(adapter.parseCallback(cancelled).success()).isFalse();

        assertThatThrownBy(() -> adapter.parseCallback(Map.of("vnp_TxnRef", "short", "vnp_Amount", "1", "vnp_ResponseCode", "00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Chưa cấu hình TMN code / secret -> checkout VNPAY trả 503, callback luôn sai chữ ký")
    void notConfigured_rejects() {
        VnpayAdapter empty = new VnpayAdapter("", "", "https://x", RETURN_URL, CLOCK);

        assertThat(empty.isConfigured()).isFalse();
        assertThatThrownBy(() -> empty.buildCheckoutUrl(payment()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED));
        assertThat(empty.verifyCallback(Map.of("vnp_SecureHash", "abc"))).isFalse();
    }

    @Test
    @DisplayName("Webhook JSON /webhooks/payments/VNPAY luôn bị từ chối")
    void jsonWebhook_alwaysRejected() {
        assertThat(adapter.verifySignature("{}".getBytes(StandardCharsets.UTF_8), "anything")).isFalse();
    }
}
