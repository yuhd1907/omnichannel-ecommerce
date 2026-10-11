package ra.edu.paymentservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ra.edu.paymentservice.service.PaymentEventHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consumer của queue payment.order-events.
 *
 * Nhận Message thô (không qua MessageConverter) và tự parse bằng JsonMapper của Jackson 3.
 * Message hỏng (thiếu messageId, JSON sai, thiếu trường, routing key lạ) -> AmqpRejectAndDontRequeueException:
 * vào DLQ ngay, không retry. Lỗi tạm thời (DB...) -> exception thường: retry có backoff rồi mới vào DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final PaymentEventHandler paymentEventHandler;
    private final JsonMapper          jsonMapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderCreatedPayload(UUID orderId, UUID userId, BigDecimal totalAmount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderCancelledPayload(UUID orderId, UUID userId) {}

    @RabbitListener(queues = RabbitTopologyConfig.QUEUE)
    public void onOrderEvent(Message message) {
        MessageProperties props = message.getMessageProperties();

        String messageId = props.getMessageId();
        if (messageId == null || messageId.isBlank()) {
            throw new AmqpRejectAndDontRequeueException("Missing messageId, routingKey=" + props.getReceivedRoutingKey());
        }

        String routingKey = props.getReceivedRoutingKey();
        log.info("Received message on {} with routingKey={}, messageId={}",
                RabbitTopologyConfig.QUEUE, routingKey, messageId);

        if (RabbitTopologyConfig.RK_ORDER_CREATED.equals(routingKey)) {
            OrderCreatedPayload payload;
            try {
                payload = jsonMapper.readValue(message.getBody(), OrderCreatedPayload.class);
            } catch (JacksonException e) {
                throw new AmqpRejectAndDontRequeueException("Malformed JSON payload for order.created (messageId=" + messageId + ")", e);
            }

            if (payload == null || payload.orderId() == null || payload.userId() == null || payload.totalAmount() == null) {
                throw new AmqpRejectAndDontRequeueException("Payload missing required fields for order.created (messageId=" + messageId + ")");
            }

            paymentEventHandler.handleOrderCreated(payload.orderId(), payload.userId(), payload.totalAmount());

        } else if (RabbitTopologyConfig.RK_ORDER_CANCELLED.equals(routingKey)) {
            OrderCancelledPayload payload;
            try {
                payload = jsonMapper.readValue(message.getBody(), OrderCancelledPayload.class);
            } catch (JacksonException e) {
                throw new AmqpRejectAndDontRequeueException("Malformed JSON payload for order.cancelled (messageId=" + messageId + ")", e);
            }

            if (payload == null || payload.orderId() == null) {
                throw new AmqpRejectAndDontRequeueException("Payload missing orderId for order.cancelled (messageId=" + messageId + ")");
            }

            paymentEventHandler.handleOrderCancelled(payload.orderId());

        } else {
            throw new AmqpRejectAndDontRequeueException(
                    "Unsupported routing key " + routingKey + " (messageId=" + messageId + ")");
        }
    }
}
