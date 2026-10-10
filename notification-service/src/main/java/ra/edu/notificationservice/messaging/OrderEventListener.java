package ra.edu.notificationservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ra.edu.notificationservice.entity.NotificationType;
import ra.edu.notificationservice.service.NotificationService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Consumer của queue notification.order-events.
 *
 * Nhận Message thô (không qua MessageConverter) và tự parse bằng JsonMapper của Jackson 3
 * (package tools.jackson.*, KHÔNG phải com.fasterxml.jackson.databind — đó là Jackson 2).
 *
 * Message hỏng (thiếu messageId, JSON sai, thiếu trường, routing key lạ) -> AmqpRejectAndDontRequeueException:
 * vào DLQ ngay, không retry. Lỗi tạm thời (DB...) -> exception thường: retry có backoff rồi mới DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private static final Map<String, NotificationType> TYPES = Map.of(
            RabbitTopologyConfig.RK_ORDER_CREATED,   NotificationType.ORDER_CREATED,
            RabbitTopologyConfig.RK_ORDER_CANCELLED, NotificationType.ORDER_CANCELLED
    );

    private final NotificationService notificationService;
    private final JsonMapper          jsonMapper;

    /** Trường cần dùng trong payload ORDER_CREATED / ORDER_CANCELLED; trường khác bỏ qua. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderEventPayload(UUID orderId, UUID userId) {}

    @RabbitListener(queues = RabbitTopologyConfig.QUEUE)
    public void onOrderEvent(Message message) {
        MessageProperties props = message.getMessageProperties();

        String messageId = props.getMessageId();
        if (messageId == null || messageId.isBlank()) {
            // Không có messageId thì không chống trùng được -> không xử lý.
            throw new AmqpRejectAndDontRequeueException("Missing messageId, routingKey=" + props.getReceivedRoutingKey());
        }

        NotificationType type = TYPES.get(props.getReceivedRoutingKey());
        if (type == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "Unsupported routing key " + props.getReceivedRoutingKey() + " (messageId=" + messageId + ")");
        }

        OrderEventPayload payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), OrderEventPayload.class);
        } catch (JacksonException e) {
            // Retry một payload hỏng 3 lần chỉ tốn thời gian -> thẳng DLQ.
            throw new AmqpRejectAndDontRequeueException("Malformed JSON payload (messageId=" + messageId + ")", e);
        }
        if (payload == null || payload.orderId() == null || payload.userId() == null) {
            throw new AmqpRejectAndDontRequeueException("Payload missing orderId/userId (messageId=" + messageId + ")");
        }

        notificationService.handleOrderEvent(messageId, type, payload.orderId(), payload.userId());
    }
}
