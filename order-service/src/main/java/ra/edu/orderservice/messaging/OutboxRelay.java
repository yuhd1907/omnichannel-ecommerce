package ra.edu.orderservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ra.edu.orderservice.entity.OutboxEvent;
import ra.edu.orderservice.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Transactional Outbox relay: đọc outbox_events PENDING và publish lên exchange order.events.
 *
 * Đảm bảo at-least-once:
 *  - Chỉ markSent khi broker ACK (publisher confirm) VÀ message không bị return (mandatory).
 *    Không có confirm thì send() thành công chỉ nghĩa là byte đã rời socket.
 *  - Mỗi event: publish -> chờ confirm -> markSent/incrementRetry trong transaction NGẮN riêng.
 *    Không giữ transaction DB trong lúc chờ mạng.
 *
 * Gửi payload dạng byte (không qua MessageConverter): payload đã là JSON trong outbox, tránh
 * phụ thuộc converter Jackson 2/3 của Spring AMQP. messageId = outbox.id để consumer chống trùng.
 *
 * Hạn chế đã biết: chạy >= 2 instance thì các relay có thể đọc cùng event và gửi trùng
 * (cần SELECT ... FOR UPDATE SKIP LOCKED). Consumer vì vậy luôn phải idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    static final int  BATCH_SIZE              = 50;
    static final int  MAX_RETRY               = 10;
    static final long CONFIRM_TIMEOUT_SECONDS = 5;

    static final String STATUS_PENDING = "PENDING";

    /** event_type trong outbox -> routing key trên exchange order.events. */
    private static final Map<String, String> ROUTING_KEYS = Map.of(
            "ORDER_CREATED",   RabbitTopologyConfig.RK_ORDER_CREATED,
            "ORDER_CANCELLED", RabbitTopologyConfig.RK_ORDER_CANCELLED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate        rabbitTemplate;
    private final TransactionTemplate   transactionTemplate;

    /** Kết quả publish một event — quyết định relay có đi tiếp trong batch hay không. */
    enum Outcome { SENT, RETRY, BROKER_UNAVAILABLE }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(STATUS_PENDING, Pageable.ofSize(BATCH_SIZE));

        for (OutboxEvent event : batch) {
            // Broker chết/treo: dừng batch, lượt sau thử lại — không đốt retry của cả batch.
            if (publish(event) == Outcome.BROKER_UNAVAILABLE) {
                break;
            }
        }
    }

    Outcome publish(OutboxEvent event) {
        String routingKey = ROUTING_KEYS.get(event.getEventType());
        if (routingKey == null) {
            // Không bao giờ gửi được -> để retry đẩy sang FAILED cho người can thiệp.
            return retry(event, "unknown eventType " + event.getEventType());
        }

        CorrelationData correlation = new CorrelationData(event.getId().toString());
        try {
            rabbitTemplate.send(RabbitTopologyConfig.ORDER_EVENTS_EXCHANGE, routingKey, toMessage(event), correlation);
        } catch (AmqpException e) {
            // Không mở được kết nối/channel: event chưa rời ứng dụng -> giữ PENDING, KHÔNG tăng retry.
            log.warn("Outbox relay: broker unavailable, keep PENDING (id={}): {}", event.getId(), e.getMessage());
            return Outcome.BROKER_UNAVAILABLE;
        }

        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            retry(event, "no publisher confirm within " + CONFIRM_TIMEOUT_SECONDS + "s");
            return Outcome.BROKER_UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.BROKER_UNAVAILABLE;
        } catch (ExecutionException e) {
            return retry(event, "confirm failed: " + e.getCause());
        }

        if (!confirm.ack()) {
            return retry(event, "broker nack: " + confirm.reason());
        }
        // Spring AMQP gán returned TRƯỚC khi complete future -> đọc ở đây là an toàn.
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            return retry(event, "returned " + returned.getReplyCode() + " " + returned.getReplyText()
                    + " (no queue bound to " + returned.getRoutingKey() + ")");
        }

        transactionTemplate.executeWithoutResult(status -> outboxEventRepository.markSent(event.getId()));
        log.debug("Outbox relay: sent id={} type={} key={}", event.getId(), event.getEventType(), routingKey);
        return Outcome.SENT;
    }

    private Outcome retry(OutboxEvent event, String reason) {
        transactionTemplate.executeWithoutResult(
                status -> outboxEventRepository.incrementRetry(event.getId(), MAX_RETRY));
        int attempt = event.getRetryCount() + 1;
        if (attempt >= MAX_RETRY) {
            log.error("Outbox relay: event FAILED after {} attempts, cần can thiệp tay (id={}, type={}): {}",
                    attempt, event.getId(), event.getEventType(), reason);
        } else {
            log.warn("Outbox relay: retry {}/{} (id={}, type={}): {}",
                    attempt, MAX_RETRY, event.getId(), event.getEventType(), reason);
        }
        return Outcome.RETRY;
    }

    private Message toMessage(OutboxEvent event) {
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setMessageId(event.getId().toString());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (event.getCreatedAt() != null) {
            props.setTimestamp(Date.from(event.getCreatedAt()));
        }
        props.setHeader("eventType", event.getEventType());
        props.setHeader("aggregateId", event.getAggregateId().toString());
        return new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), props);
    }
}
