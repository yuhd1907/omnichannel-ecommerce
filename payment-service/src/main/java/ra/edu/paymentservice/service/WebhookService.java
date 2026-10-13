package ra.edu.paymentservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.common.response.ErrorCode;
import ra.edu.paymentservice.dto.WebhookPayload;
import ra.edu.paymentservice.entity.OutboxEvent;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.exception.BusinessException;
import ra.edu.paymentservice.gateway.PaymentGatewayAdapter;
import ra.edu.paymentservice.repository.OutboxEventRepository;
import ra.edu.paymentservice.repository.PaymentRepository;
import ra.edu.paymentservice.repository.PaymentTransactionRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Xử lý webhook từ cổng thanh toán (POST /api/v1/webhooks/payments/{method}).
 *
 * Các bước 1-6 chỉ kiểm tra, KHÔNG ghi DB: chữ ký sai, JSON sai, số tiền lệch -> từ chối mà
 * không để lại dấu vết nào. Bước 7-8 cùng một transaction: ghi transaction + kết quả payment +
 * outbox, hoặc không ghi gì.
 */
@Slf4j
@Service
public class WebhookService {

    static final String STATUS_SUCCESS = "SUCCESS";
    static final String STATUS_FAILED  = "FAILED";
    private static final Set<String> RESULT_STATUSES = Set.of(STATUS_SUCCESS, STATUS_FAILED);

    /** Kết quả xử lý — mọi nhánh đều trả 200 để cổng thanh toán không gửi lại. */
    public enum Result { PROCESSED, DUPLICATE, LATE_SUCCESS_NEEDS_REFUND, LATE_IGNORED }

    private final Map<String, PaymentGatewayAdapter> adapters;
    private final PaymentRepository            paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final OutboxEventRepository        outboxEventRepository;
    private final JsonMapper                   jsonMapper;

    public WebhookService(List<PaymentGatewayAdapter> adapters,
                          PaymentRepository paymentRepository,
                          PaymentTransactionRepository transactionRepository,
                          OutboxEventRepository outboxEventRepository,
                          JsonMapper jsonMapper) {
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(a -> a.method().toUpperCase(), Function.identity()));
        this.paymentRepository     = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper            = jsonMapper;
    }

    @Transactional
    public Result handle(String method, byte[] body, String signature) {
        // 1. Adapter theo method
        PaymentGatewayAdapter adapter = method == null ? null : adapters.get(method.toUpperCase());
        if (adapter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "Unsupported payment method: " + method, HttpStatus.NOT_FOUND);
        }

        // 2. Chữ ký trên đúng mảng byte nhận được — sai thì dừng, không ghi DB
        if (!adapter.verifySignature(body, signature)) {
            log.warn("Webhook rejected: invalid signature (method={})", adapter.method());
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE,
                    "Invalid webhook signature", HttpStatus.UNAUTHORIZED);
        }

        // 3. Parse JSON
        WebhookPayload payload;
        try {
            payload = jsonMapper.readValue(body, WebhookPayload.class);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Malformed webhook payload", HttpStatus.BAD_REQUEST);
        }
        if (payload == null || payload.orderId() == null || payload.amount() == null
                || payload.gatewayTxnId() == null || payload.gatewayTxnId().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Webhook payload requires orderId, gatewayTxnId, status, amount", HttpStatus.BAD_REQUEST);
        }

        // 4. Status hợp lệ
        if (!RESULT_STATUSES.contains(payload.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Webhook status must be SUCCESS or FAILED, got: " + payload.status(), HttpStatus.BAD_REQUEST);
        }

        // 5-8. Áp kết quả vào payment (dùng chung với VNPAY IPN và trang thanh toán MOCK)
        return applyResult(payload, new String(body, StandardCharsets.UTF_8));
    }

    /**
     * Áp kết quả thanh toán ĐÃ được xác thực (chữ ký đúng) vào payment: kiểm số tiền, ghi giao
     * dịch (chống lặp bằng gateway_txn_id UNIQUE), chuyển payment khỏi PENDING và ghi outbox.
     * Dùng chung cho webhook JSON, VNPAY IPN/return và trang thanh toán MOCK.
     *
     * @param rawJson nội dung gốc từ cổng, lưu vào payment_transactions.raw_response (jsonb)
     * @throws BusinessException 404 PAYMENT_NOT_FOUND, 400 AMOUNT_MISMATCH
     */
    @Transactional
    public Result applyResult(WebhookPayload payload, String rawJson) {
        // 5. Payment theo orderId
        Payment payment = paymentRepository.findByOrderId(payload.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found for order " + payload.orderId(), HttpStatus.NOT_FOUND));

        // 6. Số tiền: compareTo, không equals (120000.0 và 120000.00 khác scale nhưng bằng nhau)
        if (payment.getAmount().compareTo(payload.amount()) != 0) {
            log.warn("Webhook rejected: amount mismatch orderId={} expected={} got={} txn={}",
                    payload.orderId(), payment.getAmount(), payload.amount(), payload.gatewayTxnId());
            throw new BusinessException(ErrorCode.AMOUNT_MISMATCH,
                    "Webhook amount does not match payment amount", HttpStatus.BAD_REQUEST);
        }

        // 7. Ghi giao dịch; 0 = gateway_txn_id đã có -> webhook lặp, trả 200 và dừng
        int inserted = transactionRepository.insertIfAbsent(
                payment.getId(), payload.gatewayTxnId(), payload.status(), payload.amount(), rawJson);
        if (inserted == 0) {
            log.info("Duplicate webhook ignored: txn={} orderId={}", payload.gatewayTxnId(), payload.orderId());
            return Result.DUPLICATE;
        }

        // 8. Kết quả payment — chỉ thắng khi còn PENDING
        int updated = paymentRepository.markResultIfPending(payload.orderId(), payload.status());
        if (updated == 1) {
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("Payment")
                    .aggregateId(payment.getId())
                    .eventType(STATUS_SUCCESS.equals(payload.status()) ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED")
                    .payload(outboxPayload(payment, payload))
                    .build());
            log.info("Payment {} for orderId={} via txn={}", payload.status(), payload.orderId(), payload.gatewayTxnId());
            return Result.PROCESSED;
        }

        if (STATUS_SUCCESS.equals(payload.status())) {
            // Tiền đã thu nhưng payment đã FAILED (đơn hủy/hết hạn, hàng đã nhả): KHÔNG tự xác nhận
            // lại đơn. Đánh dấu để admin đối soát và hoàn tiền thủ công.
            paymentRepository.markNeedsRefund(payload.orderId());
            log.error("LATE SUCCESS webhook: money captured but payment is no longer PENDING -> needs_refund=true "
                    + "(orderId={}, paymentId={}, txn={}, amount={})",
                    payload.orderId(), payment.getId(), payload.gatewayTxnId(), payload.amount());
            return Result.LATE_SUCCESS_NEEDS_REFUND;
        }

        log.info("Late FAILED webhook ignored, payment no longer PENDING (orderId={}, txn={})",
                payload.orderId(), payload.gatewayTxnId());
        return Result.LATE_IGNORED;
    }

    private String outboxPayload(Payment payment, WebhookPayload payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("paymentId", payment.getId());
        event.put("orderId", payload.orderId());
        event.put("amount", payment.getAmount());
        event.put("gatewayTxnId", payload.gatewayTxnId());
        event.put("occurredAt", Instant.now().toString());
        return jsonMapper.writeValueAsString(event);
    }
}
