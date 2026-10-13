package ra.edu.paymentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ra.edu.common.response.ErrorCode;
import ra.edu.paymentservice.dto.WebhookPayload;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.exception.BusinessException;
import ra.edu.paymentservice.gateway.VnpayAdapter;
import ra.edu.paymentservice.repository.PaymentRepository;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnpayCallbackServiceTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String TXN_REF = PAYMENT_ID.toString().replace("-", "") + "20261013100000";
    private static final Map<String, String> PARAMS = Map.of("vnp_TxnRef", TXN_REF, "vnp_SecureHash", "x");

    @Mock private VnpayAdapter vnpayAdapter;
    @Mock private PaymentRepository paymentRepository;
    @Mock private WebhookService webhookService;

    private VnpayCallbackService service;

    @BeforeEach
    void setUp() {
        service = new VnpayCallbackService(vnpayAdapter, paymentRepository, webhookService, JsonMapper.builder().build());
    }

    private void validCallback(boolean success) {
        when(vnpayAdapter.verifyCallback(PARAMS)).thenReturn(true);
        when(vnpayAdapter.parseCallback(PARAMS)).thenReturn(new VnpayAdapter.CallbackResult(
                PAYMENT_ID, TXN_REF, new BigDecimal("240000"), success, success ? "00" : "24", "14123456"));
        Payment payment = Payment.builder().id(PAYMENT_ID).orderId(ORDER_ID).userId(UUID.randomUUID())
                .amount(new BigDecimal("240000.00")).build();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    @DisplayName("IPN hợp lệ, thanh toán thành công -> applyResult(SUCCESS, VNPAY-<txnRef>) và RspCode 00")
    void ipn_success_confirms() {
        validCallback(true);
        when(webhookService.applyResult(any(), anyString())).thenReturn(WebhookService.Result.PROCESSED);

        VnpayCallbackService.IpnResponse res = service.handleIpn(PARAMS);

        assertThat(res.rspCode()).isEqualTo("00");
        ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);
        verify(webhookService).applyResult(captor.capture(), anyString());
        assertThat(captor.getValue().orderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().status()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().gatewayTxnId()).isEqualTo("VNPAY-" + TXN_REF);
    }

    @Test
    @DisplayName("IPN gửi lại (đã xử lý) -> RspCode 02")
    void ipn_duplicate_02() {
        validCallback(true);
        when(webhookService.applyResult(any(), anyString())).thenReturn(WebhookService.Result.DUPLICATE);

        assertThat(service.handleIpn(PARAMS).rspCode()).isEqualTo("02");
    }

    @Test
    @DisplayName("Sai chữ ký -> RspCode 97, không đụng DB")
    void ipn_badChecksum_97() {
        when(vnpayAdapter.verifyCallback(PARAMS)).thenReturn(false);

        assertThat(service.handleIpn(PARAMS).rspCode()).isEqualTo("97");
        verifyNoInteractions(paymentRepository, webhookService);
    }

    @Test
    @DisplayName("Số tiền lệch -> RspCode 04; không có payment -> 01")
    void ipn_amountMismatch_04_and_notFound_01() {
        validCallback(true);
        when(webhookService.applyResult(any(), anyString())).thenThrow(
                new BusinessException(ErrorCode.AMOUNT_MISMATCH, "x", HttpStatus.BAD_REQUEST));
        assertThat(service.handleIpn(PARAMS).rspCode()).isEqualTo("04");

        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());
        assertThat(service.handleIpn(PARAMS).rspCode()).isEqualTo("01");
    }

    @Test
    @DisplayName("Return URL: áp kết quả và trả orderId + kết quả (khách hủy -> FAILED)")
    void return_appliesAndReportsResult() {
        validCallback(false);
        when(webhookService.applyResult(any(), anyString())).thenReturn(WebhookService.Result.PROCESSED);

        VnpayCallbackService.ReturnResult res = service.handleReturn(PARAMS);

        assertThat(res.orderId()).isEqualTo(ORDER_ID);
        assertThat(res.result()).isEqualTo("FAILED");
        assertThat(res.responseCode()).isEqualTo("24");
    }

    @Test
    @DisplayName("Return URL sai chữ ký -> INVALID_SIGNATURE, không đụng DB")
    void return_badChecksum() {
        when(vnpayAdapter.verifyCallback(PARAMS)).thenReturn(false);

        assertThat(service.handleReturn(PARAMS).result()).isEqualTo("INVALID_SIGNATURE");
        verifyNoInteractions(paymentRepository, webhookService);
    }
}
