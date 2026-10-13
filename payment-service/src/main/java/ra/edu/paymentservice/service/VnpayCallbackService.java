package ra.edu.paymentservice.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ra.edu.common.response.ErrorCode;
import ra.edu.paymentservice.dto.WebhookPayload;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.exception.BusinessException;
import ra.edu.paymentservice.gateway.VnpayAdapter;
import ra.edu.paymentservice.repository.PaymentRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Xử lý kết quả VNPAY gửi về: IPN (server-to-server) và return URL (trình duyệt).
 *
 * Cả hai đều kiểm chữ ký rồi đi qua WebhookService.applyResult — cùng một chỗ chống lặp
 * (gateway_txn_id = "VNPAY-" + vnp_TxnRef, UNIQUE) nên IPN và return URL về theo thứ tự nào
 * cũng chỉ áp kết quả một lần.
 *
 * Vì sao return URL CŨNG áp kết quả: khi chạy local, VNPAY không gọi được IPN vào localhost.
 * Tham số return URL cũng được VNPAY ký nên an toàn như IPN; trên VPS thì IPN thường về trước,
 * return URL nhận DUPLICATE. Hệ thống không có giao diện nên return URL trả JSON kết quả.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayCallbackService {

    private final VnpayAdapter      vnpayAdapter;
    private final PaymentRepository paymentRepository;
    private final WebhookService    webhookService;
    private final JsonMapper        jsonMapper;

    /** Phản hồi IPN đúng định dạng VNPAY yêu cầu: {"RspCode":"00","Message":"Confirm Success"}. */
    public record IpnResponse(@JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {
        static IpnResponse of(String code, String message) { return new IpnResponse(code, message); }
    }

    /** Kết quả áp vào hệ thống — dùng chung cho IPN và return URL. */
    enum Outcome { CONFIRMED, ALREADY_CONFIRMED, INVALID_SIGNATURE, NOT_FOUND, INVALID_AMOUNT, ERROR }

    record Applied(Outcome outcome, VnpayAdapter.CallbackResult result, Payment payment) {}

    public IpnResponse handleIpn(Map<String, String> params) {
        Applied applied = apply(params, "ipn");
        return switch (applied.outcome()) {
            case CONFIRMED         -> IpnResponse.of("00", "Confirm Success");
            case ALREADY_CONFIRMED -> IpnResponse.of("02", "Order already confirmed");
            case NOT_FOUND         -> IpnResponse.of("01", "Order not found");
            case INVALID_AMOUNT    -> IpnResponse.of("04", "Invalid amount");
            case INVALID_SIGNATURE -> IpnResponse.of("97", "Invalid Checksum");
            case ERROR             -> IpnResponse.of("99", "Unknown error");
        };
    }

    /** Kết quả trả cho trình duyệt ở return URL. result: SUCCESS | FAILED | INVALID_SIGNATURE | NOT_FOUND | INVALID_AMOUNT | ERROR. */
    public record ReturnResult(UUID orderId, String result, String responseCode) {}

    public ReturnResult handleReturn(Map<String, String> params) {
        Applied applied = apply(params, "return");
        String result = switch (applied.outcome()) {
            case CONFIRMED, ALREADY_CONFIRMED -> applied.result().success() ? "SUCCESS" : "FAILED";
            default -> applied.outcome().name();
        };
        return new ReturnResult(
                applied.payment() == null ? null : applied.payment().getOrderId(),
                result,
                applied.result() == null ? null : applied.result().responseCode());
    }

    Applied apply(Map<String, String> params, String channel) {
        if (!vnpayAdapter.verifyCallback(params)) {
            log.warn("VNPAY {} rejected: invalid checksum (txnRef={})", channel, params.get("vnp_TxnRef"));
            return new Applied(Outcome.INVALID_SIGNATURE, null, null);
        }

        VnpayAdapter.CallbackResult result;
        try {
            result = vnpayAdapter.parseCallback(params);
        } catch (IllegalArgumentException e) {
            log.warn("VNPAY {} rejected: {}", channel, e.getMessage());
            return new Applied(Outcome.NOT_FOUND, null, null);
        }

        Optional<Payment> payment = paymentRepository.findById(result.paymentId());
        if (payment.isEmpty()) {
            return new Applied(Outcome.NOT_FOUND, result, null);
        }

        WebhookPayload payload = new WebhookPayload(
                payment.get().getOrderId(),
                "VNPAY-" + result.txnRef(),
                result.success() ? "SUCCESS" : "FAILED",
                result.amount());
        try {
            WebhookService.Result r = webhookService.applyResult(payload, jsonMapper.writeValueAsString(new TreeMap<>(params)));
            log.info("VNPAY {}: txnRef={} responseCode={} -> {}", channel, result.txnRef(), result.responseCode(), r);
            Outcome outcome = r == WebhookService.Result.PROCESSED ? Outcome.CONFIRMED : Outcome.ALREADY_CONFIRMED;
            return new Applied(outcome, result, payment.get());
        } catch (BusinessException e) {
            Outcome outcome = switch (e.getCode()) {
                case ErrorCode.AMOUNT_MISMATCH   -> Outcome.INVALID_AMOUNT;
                case ErrorCode.PAYMENT_NOT_FOUND -> Outcome.NOT_FOUND;
                default -> Outcome.ERROR;
            };
            return new Applied(outcome, result, payment.get());
        } catch (RuntimeException e) {
            log.error("VNPAY {} failed (txnRef={})", channel, result.txnRef(), e);
            return new Applied(Outcome.ERROR, result, payment.get());
        }
    }
}
