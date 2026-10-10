package ra.edu.orderservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ra.edu.orderservice.entity.OutboxEvent;
import ra.edu.orderservice.repository.OutboxEventRepository;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        // TransactionTemplate thật trên transaction manager giả: callback vẫn được chạy.
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        relay = new OutboxRelay(outboxEventRepository, rabbitTemplate, tx);
    }

    private static OutboxEvent event(String type) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType(type)
                .payload("{\"orderId\":\"o-1\",\"totalAmount\":120000.00}")
                .createdAt(Instant.parse("2026-10-09T10:00:00Z"))
                .build();
    }

    /** Giả lập broker trả confirm cho lần send() tiếp theo. */
    private void brokerConfirms(boolean ack, ReturnedMessage returned) {
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            if (returned != null) {
                cd.setReturned(returned);
            }
            cd.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack-reason"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("ACK, không bị return -> markSent; message giữ nguyên payload byte + messageId/headers")
    void ack_marksSent_withRawPayloadAndHeaders() {
        OutboxEvent e = event("ORDER_CREATED");
        brokerConfirms(true, null);

        assertThat(relay.publish(e)).isEqualTo(OutboxRelay.Outcome.SENT);

        ArgumentCaptor<Message> msg = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("order.events"), eq("order.created"), msg.capture(), any(CorrelationData.class));
        MessageProperties p = msg.getValue().getMessageProperties();
        assertThat(new String(msg.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo(e.getPayload());
        assertThat(p.getContentType()).isEqualTo("application/json");
        assertThat(p.getMessageId()).isEqualTo(e.getId().toString());
        assertThat((String) p.getHeader("eventType")).isEqualTo("ORDER_CREATED");
        assertThat((String) p.getHeader("aggregateId")).isEqualTo(e.getAggregateId().toString());

        verify(outboxEventRepository).markSent(e.getId());
        verify(outboxEventRepository, never()).incrementRetry(any(), anyInt());
    }

    @Test
    @DisplayName("ORDER_CANCELLED -> routing key order.cancelled")
    void cancelled_routesToOrderCancelled() {
        brokerConfirms(true, null);

        relay.publish(event("ORDER_CANCELLED"));

        verify(rabbitTemplate).send(eq("order.events"), eq("order.cancelled"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("Broker NACK -> incrementRetry, không markSent")
    void nack_incrementsRetry() {
        OutboxEvent e = event("ORDER_CREATED");
        brokerConfirms(false, null);

        assertThat(relay.publish(e)).isEqualTo(OutboxRelay.Outcome.RETRY);

        verify(outboxEventRepository).incrementRetry(e.getId(), OutboxRelay.MAX_RETRY);
        verify(outboxEventRepository, never()).markSent(any());
    }

    @Test
    @DisplayName("ACK nhưng bị return (không queue nào bind) -> incrementRetry, không markSent")
    void returned_incrementsRetry() {
        OutboxEvent e = event("ORDER_CREATED");
        ReturnedMessage returned = new ReturnedMessage(
                new Message(new byte[0]), 312, "NO_ROUTE", "order.events", "order.created");
        brokerConfirms(true, returned);

        assertThat(relay.publish(e)).isEqualTo(OutboxRelay.Outcome.RETRY);

        verify(outboxEventRepository).incrementRetry(e.getId(), OutboxRelay.MAX_RETRY);
        verify(outboxEventRepository, never()).markSent(any());
    }

    @Test
    @DisplayName("Broker chết -> dừng batch, giữ PENDING, KHÔNG tăng retry")
    void brokerDown_stopsBatchWithoutBurningRetry() {
        OutboxEvent first = event("ORDER_CREATED");
        OutboxEvent second = event("ORDER_CREATED");
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.relay();

        verify(rabbitTemplate, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        verify(outboxEventRepository, never()).incrementRetry(any(), anyInt());
        verify(outboxEventRepository, never()).markSent(any());
    }

    @Test
    @DisplayName("eventType lạ -> không gửi, incrementRetry để cuối cùng thành FAILED")
    void unknownEventType_isNotSent() {
        OutboxEvent e = event("SOMETHING_ELSE");

        assertThat(relay.publish(e)).isEqualTo(OutboxRelay.Outcome.RETRY);

        verifyNoInteractions(rabbitTemplate);
        verify(outboxEventRepository).incrementRetry(e.getId(), OutboxRelay.MAX_RETRY);
    }

    @Test
    @DisplayName("relay() đọc batch PENDING kích thước 50 theo created_at")
    void relay_readsPendingBatchOf50() {
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(Pageable.class)))
                .thenReturn(List.of());

        relay.relay();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(outboxEventRepository).findByStatusOrderByCreatedAtAsc(eq("PENDING"), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(50);
    }
}
