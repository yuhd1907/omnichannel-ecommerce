package ra.edu.notificationservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ra.edu.notificationservice.entity.NotificationType;
import ra.edu.notificationservice.service.NotificationService;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    private static final UUID ORDER_ID = UUID.fromString("4164d0d7-71a6-458d-b1ef-49b09b9bda3a");
    private static final UUID USER_ID  = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private NotificationService notificationService;

    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderEventListener(notificationService, JsonMapper.builder().build());
    }

    private static Message message(String messageId, String routingKey, String body) {
        MessageProperties p = new MessageProperties();
        p.setMessageId(messageId);
        p.setReceivedRoutingKey(routingKey);
        p.setContentType("application/json");
        return new Message(body.getBytes(StandardCharsets.UTF_8), p);
    }

    @Test
    @DisplayName("order.created hợp lệ -> gọi service với đúng type/orderId/userId; trường thừa bị bỏ qua")
    void validOrderCreated_delegatesToService() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID
                + "\",\"channel\":\"WEB\",\"totalAmount\":120000.00,\"skus\":{\"AO-A-DO-M\":1}}";

        listener.onOrderEvent(message("msg-1", "order.created", body));

        verify(notificationService).handleOrderEvent("msg-1", NotificationType.ORDER_CREATED, ORDER_ID, USER_ID, null, null,
                new java.math.BigDecimal("120000.00"));
    }

    @Test
    @DisplayName("order.cancelled -> type ORDER_CANCELLED")
    void orderCancelled_mapsType() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\"}";

        listener.onOrderEvent(message("msg-2", "order.cancelled", body));

        verify(notificationService).handleOrderEvent("msg-2", NotificationType.ORDER_CANCELLED, ORDER_ID, USER_ID, null, null, null);
    }

    @Test
    @DisplayName("JSON hỏng -> AmqpRejectAndDontRequeueException (thẳng DLQ), không gọi service")
    void malformedJson_rejectedWithoutRequeue() {
        assertThatThrownBy(() -> listener.onOrderEvent(message("msg-3", "order.created", "{not json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("UUID sai định dạng -> reject, không gọi service")
    void invalidUuid_rejected() {
        assertThatThrownBy(() -> listener.onOrderEvent(
                message("msg-4", "order.created", "{\"orderId\":\"abc\",\"userId\":\"" + USER_ID + "\"}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Thiếu userId -> reject")
    void missingField_rejected() {
        assertThatThrownBy(() -> listener.onOrderEvent(
                message("msg-5", "order.created", "{\"orderId\":\"" + ORDER_ID + "\"}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Thiếu messageId -> reject (không chống trùng được)")
    void missingMessageId_rejected() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\"}";
        assertThatThrownBy(() -> listener.onOrderEvent(message(null, "order.created", body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("order.shipped / order.completed -> type ORDER_SHIPPED / ORDER_COMPLETED")
    void fulfillmentEvents_mapTypes() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\",\"status\":\"SHIPPING\"}";

        listener.onOrderEvent(message("msg-7", "order.shipped", body));
        listener.onOrderEvent(message("msg-8", "order.completed", body));

        verify(notificationService).handleOrderEvent("msg-7", NotificationType.ORDER_SHIPPED, ORDER_ID, USER_ID, null, null, null);
        verify(notificationService).handleOrderEvent("msg-8", NotificationType.ORDER_COMPLETED, ORDER_ID, USER_ID, null, null, null);
    }

    @Test
    @DisplayName("Payload có customerEmail -> truyền email xuống service để gửi mail")
    void customerEmail_passedThrough() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID
                + "\",\"customerEmail\":\"buyer@example.com\"}";

        listener.onOrderEvent(message("msg-9", "order.confirmed", body));

        verify(notificationService).handleOrderEvent("msg-9", NotificationType.ORDER_CONFIRMED, ORDER_ID, USER_ID,
                "buyer@example.com", null, null);
    }

    @Test
    @DisplayName("Routing key lạ -> reject")
    void unknownRoutingKey_rejected() {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\"}";
        assertThatThrownBy(() -> listener.onOrderEvent(message("msg-6", "order.refunded", body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }
}
