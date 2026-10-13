package ra.edu.paymentservice.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ra.edu.common.response.ErrorCode;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cổng VNPAY (sandbox) theo API v2.1.0 — https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
 *
 * Luồng: checkout trả URL tới trang thanh toán VNPAY (có chữ ký) -> người mua trả tiền ->
 *  - VNPAY gọi server-to-server GET /api/v1/payments/vnpay/ipn  (nguồn kết quả chính thức);
 *  - trình duyệt quay về GET /api/v1/payments/vnpay/return     (xem VnpayCallbackService).
 *
 * Chữ ký: HMAC-SHA512(hashSecret, chuỗi query các tham số vnp_* đã sắp theo tên, giá trị
 * URL-encode). Chiều về: bỏ vnp_SecureHash / vnp_SecureHashType, dựng lại chuỗi và so khớp.
 *
 * vnp_TxnRef = paymentId (32 hex) + thời điểm tạo URL (yyyyMMddHHmmss): mỗi lần mở trang thanh
 * toán là một mã giao dịch mới (VNPAY không cho dùng lại TxnRef), và vẫn suy ra được paymentId.
 *
 * Chưa cấu hình VNPAY_TMN_CODE / VNPAY_HASH_SECRET -> checkout bằng VNPAY trả 503.
 */
@Component
public class VnpayAdapter implements PaymentGatewayAdapter {

    static final String VERSION = "2.1.0";
    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** Bằng thời hạn giữ hàng của đơn (order-service hủy đơn PENDING sau 15 phút). */
    static final int EXPIRE_MINUTES = 15;

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final Clock  clock;

    @Autowired
    public VnpayAdapter(
            @Value("${app.payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${app.payment.vnpay.hash-secret:}") String hashSecret,
            @Value("${app.payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${app.payment.vnpay.return-url:http://localhost:8080/api/v1/payments/vnpay/return}") String returnUrl) {
        this(tmnCode, hashSecret, payUrl, returnUrl, Clock.system(VN_ZONE));
    }

    VnpayAdapter(String tmnCode, String hashSecret, String payUrl, String returnUrl, Clock clock) {
        this.tmnCode    = tmnCode == null ? "" : tmnCode.trim();
        this.hashSecret = hashSecret == null ? "" : hashSecret.trim();
        this.payUrl     = payUrl;
        this.returnUrl  = returnUrl;
        this.clock      = clock;
    }

    @Override
    public String method() {
        return "VNPAY";
    }

    public boolean isConfigured() {
        return !tmnCode.isEmpty() && !hashSecret.isEmpty();
    }

    @Override
    public String buildCheckoutUrl(Payment payment) {
        return buildCheckoutUrl(payment, "127.0.0.1");
    }

    @Override
    public String buildCheckoutUrl(Payment payment, String clientIp) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED,
                    "VNPAY chưa được cấu hình (VNPAY_TMN_CODE / VNPAY_HASH_SECRET)",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(VN_ZONE));

        SortedMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(toVnpAmount(payment.getAmount())));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef(payment.getId(), now));
        // Không dấu, không ký tự đặc biệt theo khuyến cáo của VNPAY
        params.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", now.format(VNP_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(EXPIRE_MINUTES).format(VNP_DATE));

        String query = canonicalQuery(params);
        return payUrl + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
    }

    /** VNPAY không gửi webhook JSON vào /api/v1/webhooks/payments/VNPAY -> luôn từ chối đường đó. */
    @Override
    public boolean verifySignature(byte[] body, String signature) {
        return false;
    }

    /** Kiểm chữ ký của tham số VNPAY gửi về (IPN hoặc return URL). */
    public boolean verifyCallback(Map<String, String> params) {
        if (!isConfigured() || params == null) {
            return false;
        }
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return false;
        }
        SortedMap<String, String> signed = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")
                    && v != null && !v.isEmpty()) {
                signed.put(k, v);
            }
        });
        String expected = hmacSha512(canonicalQuery(signed));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                received.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    /** Kết quả VNPAY đã được đọc ra từ tham số callback (chỉ gọi sau verifyCallback). */
    public record CallbackResult(UUID paymentId, String txnRef, BigDecimal amount, boolean success,
                                 String responseCode, String transactionNo) {}

    /**
     * @throws IllegalArgumentException khi thiếu tham số hoặc vnp_TxnRef sai định dạng.
     */
    public CallbackResult parseCallback(Map<String, String> params) {
        String txnRef = require(params, "vnp_TxnRef");
        String amount = require(params, "vnp_Amount");
        String responseCode = require(params, "vnp_ResponseCode");
        // Thành công khi CẢ mã phản hồi và trạng thái giao dịch đều 00 (theo tài liệu VNPAY)
        String txnStatus = params.getOrDefault("vnp_TransactionStatus", responseCode);
        boolean success = "00".equals(responseCode) && "00".equals(txnStatus);
        return new CallbackResult(paymentIdOf(txnRef), txnRef,
                new BigDecimal(amount).movePointLeft(2), success, responseCode, params.get("vnp_TransactionNo"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    static long toVnpAmount(BigDecimal amount) {
        // VNPAY nhận số tiền nhân 100 (bỏ phần thập phân)
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static String txnRef(UUID paymentId, ZonedDateTime at) {
        return paymentId.toString().replace("-", "") + at.format(VNP_DATE);
    }

    static UUID paymentIdOf(String txnRef) {
        if (txnRef == null || txnRef.length() < 32) {
            throw new IllegalArgumentException("Invalid vnp_TxnRef: " + txnRef);
        }
        String hex = txnRef.substring(0, 32);
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32));
    }

    /** k1=v1&k2=v2 theo thứ tự tên, giá trị URL-encode US-ASCII (khoảng trắng thành +) như code mẫu VNPAY. */
    static String canonicalQuery(SortedMap<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }

    String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA512 not available", e);
        }
    }

    private static String require(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v;
    }
}
