package ra.edu.orderservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ra.edu.orderservice.service.OrderStateService;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    private static final UUID ORDER_ID = UUID.fromString("4164d0d7-71a6-458d-b1ef-49b09b9bda3a");

    @Mock
    private OrderStateService orderStateService;

    private PaymentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentEventListener(orderStateService, JsonMapper.builder().build());
    }

    private static Message message(String eventType, String body) {
        MessageProperties p = new MessageProperties();
        p.setMessageId("msg-1");
        if (eventType != null) {
            p.setHeader("eventType", eventType);
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), p);
    }

    private static final String BODY = "{\"paymentId\":\"" + UUID.randomUUID() + "\",\"orderId\":\"" + ORDER_ID
            + "\",\"amount\":240000.00,\"gatewayTxnId\":\"TXN-1\",\"occurredAt\":\"2026-10-12T00:00:00Z\"}";

    @Test
    @DisplayName("PAYMENT_SUCCEEDED -> confirmPaid(orderId)")
    void succeeded_confirms() {
        listener.onPaymentEvent(message("PAYMENT_SUCCEEDED", BODY));
        verify(orderStateService).confirmPaid(ORDER_ID);
        verifyNoMoreInteractions(orderStateService);
    }

    @Test
    @DisplayName("PAYMENT_FAILED -> cancelAndRelease(orderId, FAILED, PAYMENT_FAILED)")
    void failed_cancels() {
        listener.onPaymentEvent(message("PAYMENT_FAILED", BODY));
        verify(orderStateService).cancelAndRelease(ORDER_ID, "FAILED", "PAYMENT_FAILED");
        verifyNoMoreInteractions(orderStateService);
    }

    @Test
    @DisplayName("eventType lạ hoặc thiếu -> bỏ qua, không lỗi")
    void unknownType_ignored() {
        listener.onPaymentEvent(message("PAYMENT_REFUNDED", BODY));
        listener.onPaymentEvent(message(null, BODY));
        verifyNoInteractions(orderStateService);
    }

    @Test
    @DisplayName("JSON hỏng -> AmqpRejectAndDontRequeueException (DLQ ngay)")
    void malformed_rejected() {
        assertThatThrownBy(() -> listener.onPaymentEvent(message("PAYMENT_SUCCEEDED", "{not json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(orderStateService);
    }

    @Test
    @DisplayName("Thiếu orderId -> reject")
    void missingOrderId_rejected() {
        assertThatThrownBy(() -> listener.onPaymentEvent(message("PAYMENT_SUCCEEDED", "{\"amount\":1}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(orderStateService);
    }
}
