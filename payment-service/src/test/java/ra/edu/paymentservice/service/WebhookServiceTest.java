package ra.edu.paymentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import ra.edu.paymentservice.entity.OutboxEvent;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.exception.BusinessException;
import ra.edu.paymentservice.gateway.PaymentGatewayAdapter;
import ra.edu.paymentservice.repository.OutboxEventRepository;
import ra.edu.paymentservice.repository.PaymentRepository;
import ra.edu.paymentservice.repository.PaymentTransactionRepository;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookServiceTest {

    private static final UUID ORDER_ID   = UUID.fromString("4164d0d7-71a6-458d-b1ef-49b09b9bda3a");
    private static final UUID PAYMENT_ID = UUID.fromString("9a9a9a9a-0000-0000-0000-000000000001");

    @Mock private PaymentGatewayAdapter        adapter;
    @Mock private PaymentRepository            paymentRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private OutboxEventRepository        outboxEventRepository;

    private WebhookService service;

    @BeforeEach
    void setUp() {
        when(adapter.method()).thenReturn("MOCK");
        when(adapter.verifySignature(any(), eq("good-sig"))).thenReturn(true);
        service = new WebhookService(List.of(adapter), paymentRepository, transactionRepository,
                outboxEventRepository, JsonMapper.builder().build());

        Payment payment = Payment.builder().id(PAYMENT_ID).orderId(ORDER_ID).userId(UUID.randomUUID())
                .amount(new BigDecimal("240000.00")).status("PENDING").build();
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(transactionRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentRepository.markResultIfPending(any(), any())).thenReturn(1);
    }

    private static byte[] body(String status, String amount) {
        return ("{\"orderId\":\"" + ORDER_ID + "\",\"gatewayTxnId\":\"TXN-1\",\"status\":\"" + status
                + "\",\"amount\":" + amount + "}").getBytes(StandardCharsets.UTF_8);
    }

    private static void assertRejected(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    private void verifyNoWrites() {
        verify(transactionRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
        verify(paymentRepository, never()).markResultIfPending(any(), any());
        verify(paymentRepository, never()).markNeedsRefund(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("1. method lạ -> 404")
    void unknownMethod_404() {
        assertRejected(() -> service.handle("paypal", body("SUCCESS", "240000"), "good-sig"),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
        verifyNoWrites();
    }

    @Test
    @DisplayName("2. chữ ký sai -> 401 INVALID_SIGNATURE, không ghi DB, không đọc payment")
    void badSignature_401_noDbAccess() {
        assertRejected(() -> service.handle("mock", body("SUCCESS", "240000"), "bad-sig"),
                HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE");
        verifyNoWrites();
        verify(paymentRepository, never()).findByOrderId(any());
    }

    @Test
    @DisplayName("3. JSON hỏng -> 400 VALIDATION_FAILED")
    void malformedJson_400() {
        assertRejected(() -> service.handle("mock", "{not json".getBytes(StandardCharsets.UTF_8), "good-sig"),
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        verifyNoWrites();
    }

    @Test
    @DisplayName("4. status lạ -> 400")
    void unknownStatus_400() {
        assertRejected(() -> service.handle("mock", body("PAID", "240000"), "good-sig"),
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        verifyNoWrites();
    }

    @Test
    @DisplayName("5. không có payment -> 404 PAYMENT_NOT_FOUND")
    void paymentNotFound_404() {
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        assertRejected(() -> service.handle("mock", body("SUCCESS", "240000"), "good-sig"),
                HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND");
        verifyNoWrites();
    }

    @Test
    @DisplayName("6. số tiền lệch -> 400 AMOUNT_MISMATCH, payment giữ PENDING (không ghi gì)")
    void amountMismatch_400() {
        assertRejected(() -> service.handle("mock", body("SUCCESS", "1000"), "good-sig"),
                HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH");
        verifyNoWrites();
    }

    @Test
    @DisplayName("6. so tiền bằng compareTo: 240000 == 240000.00 dù khác scale")
    void amountDifferentScale_accepted() {
        assertThat(service.handle("mock", body("SUCCESS", "240000"), "good-sig"))
                .isEqualTo(WebhookService.Result.PROCESSED);
    }

    @Test
    @DisplayName("7. gateway_txn_id đã có -> DUPLICATE, không đổi payment, không outbox")
    void duplicateTxn_stops() {
        when(transactionRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(0);

        assertThat(service.handle("mock", body("SUCCESS", "240000.00"), "good-sig"))
                .isEqualTo(WebhookService.Result.DUPLICATE);
        verify(paymentRepository, never()).markResultIfPending(any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. SUCCESS thắng -> outbox PAYMENT_SUCCEEDED, payload đủ trường")
    void success_writesOutbox() {
        byte[] b = body("SUCCESS", "240000.00");

        assertThat(service.handle("mock", b, "good-sig")).isEqualTo(WebhookService.Result.PROCESSED);

        verify(transactionRepository).insertIfAbsent(eq(PAYMENT_ID), eq("TXN-1"), eq("SUCCESS"),
                argThat(a -> a.compareTo(new BigDecimal("240000")) == 0), eq(new String(b, StandardCharsets.UTF_8)));
        verify(paymentRepository).markResultIfPending(ORDER_ID, "SUCCESS");
        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("PAYMENT_SUCCEEDED");
        assertThat(ev.getValue().getAggregateType()).isEqualTo("Payment");
        assertThat(ev.getValue().getAggregateId()).isEqualTo(PAYMENT_ID);
        assertThat(ev.getValue().getPayload())
                .contains("\"paymentId\"", "\"orderId\"", "\"amount\"", "\"gatewayTxnId\":\"TXN-1\"", "\"occurredAt\"");
        verify(paymentRepository, never()).markNeedsRefund(any());
    }

    @Test
    @DisplayName("8. FAILED thắng -> outbox PAYMENT_FAILED")
    void failed_writesOutbox() {
        assertThat(service.handle("mock", body("FAILED", "240000.00"), "good-sig"))
                .isEqualTo(WebhookService.Result.PROCESSED);
        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    @DisplayName("8. SUCCESS đến khi payment không còn PENDING -> needs_refund, không outbox")
    void lateSuccess_marksNeedsRefund() {
        when(paymentRepository.markResultIfPending(any(), any())).thenReturn(0);

        assertThat(service.handle("mock", body("SUCCESS", "240000.00"), "good-sig"))
                .isEqualTo(WebhookService.Result.LATE_SUCCESS_NEEDS_REFUND);
        verify(paymentRepository).markNeedsRefund(ORDER_ID);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. FAILED đến khi payment không còn PENDING -> bỏ qua, không needs_refund")
    void lateFailed_ignored() {
        when(paymentRepository.markResultIfPending(any(), any())).thenReturn(0);

        assertThat(service.handle("mock", body("FAILED", "240000.00"), "good-sig"))
                .isEqualTo(WebhookService.Result.LATE_IGNORED);
        verify(paymentRepository, never()).markNeedsRefund(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
