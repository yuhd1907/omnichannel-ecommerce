package ra.edu.paymentservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ra.edu.paymentservice.service.PaymentEventHandler;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    private static final UUID ORDER_ID = UUID.fromString("4164d0d7-71a6-458d-b1ef-49b09b9bda3a");
    private static final UUID USER_ID  = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final BigDecimal AMOUNT = new BigDecimal("120000.00");

    @Mock
    private PaymentEventHandler paymentEventHandler;

    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderEventListener(paymentEventHandler, JsonMapper.builder().build());
    }

    private static Message message(String messageId, String routingKey, String body) {
        MessageProperties p = new MessageProperties();
        p.setMessageId(messageId);
        p.setReceivedRoutingKey(routingKey);
        p.setContentType("application/json");
        return new Message(body.getBytes(StandardCharsets.UTF_8), p);
    }

    @Test
    @DisplayName("order.created hợp lệ -> gọi paymentEventHandler.handleOrderCreated")
    void validOrderCreated_delegatesToHandler() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID
                + "\",\"channel\":\"WEB\",\"totalAmount\":120000.00,\"skus\":{\"FLASH-001\":1}}";

        listener.onOrderEvent(message("msg-1", "order.created", body));

        verify(paymentEventHandler).handleOrderCreated(ORDER_ID, USER_ID, AMOUNT);
    }

    @Test
    @DisplayName("order.cancelled hợp lệ -> gọi paymentEventHandler.handleOrderCancelled")
    void validOrderCancelled_delegatesToHandler() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\"}";

        listener.onOrderEvent(message("msg-2", "order.cancelled", body));

        verify(paymentEventHandler).handleOrderCancelled(ORDER_ID);
    }

    @Test
    @DisplayName("JSON hỏng -> ném AmqpRejectAndDontRequeueException vào DLQ ngay")
    void malformedJson_rejectedWithoutRequeue() {
        assertThatThrownBy(() -> listener.onOrderEvent(message("msg-3", "order.created", "{invalid json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(paymentEventHandler);
    }

    @Test
    @DisplayName("order.created thiếu totalAmount -> reject")
    void missingAmount_rejected() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\"}";
        assertThatThrownBy(() -> listener.onOrderEvent(message("msg-4", "order.created", body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(paymentEventHandler);
    }

    @Test
    @DisplayName("Thiếu messageId -> reject")
    void missingMessageId_rejected() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\",\"totalAmount\":120000.00}";
        assertThatThrownBy(() -> listener.onOrderEvent(message(null, "order.created", body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(paymentEventHandler);
    }

    @Test
    @DisplayName("Routing key lạ -> reject")
    void unknownRoutingKey_rejected() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\"}";
        assertThatThrownBy(() -> listener.onOrderEvent(message("msg-5", "order.shipped", body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(paymentEventHandler);
    }
}
