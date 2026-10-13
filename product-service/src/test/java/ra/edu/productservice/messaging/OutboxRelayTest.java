package ra.edu.productservice.messaging;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ra.edu.productservice.entity.OutboxEvent;
import ra.edu.productservice.repository.OutboxEventRepository;

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
                .aggregateType("Product")
                .aggregateId(UUID.randomUUID())
                .eventType(type)
                .payload("{\"skuCode\":\"NEW-SKU-01\",\"price\":120000.00}")
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
        OutboxEvent e = event("PRODUCT_SKU_CREATED");
        brokerConfirms(true, null);

        assertThat(relay.publish(e)).isEqualTo(OutboxRelay.Outcome.SENT);

        ArgumentCaptor<Message> msg = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("product.events"), eq("product.sku.created"), msg.capture(), any(CorrelationData.class));
        MessageProperties p = msg.getValue().getMessageProperties();
        assertThat(new String(msg.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo(e.getPayload());
        assertThat(p.getContentType()).isEqualTo("application/json");
        assertThat(p.getMessageId()).isEqualTo(e.getId().toString());
        assertThat((String) p.getHeader("eventType")).isEqualTo("PRODUCT_SKU_CREATED");
        assertThat((String) p.getHeader("aggregateId")).isEqualTo(e.getAggregateId().toString());

        verify(outboxEventRepository).markSent(e.getId());
        verify(outboxEventRepository, never()).incrementRetry(any(), anyInt());
    }

    @Test
    @DisplayName("Broker NACK -> incrementRetry, không markSent")
    void nack_incrementsRetry() {
        OutboxEvent e = event("PRODUCT_SKU_CREATED");
        brokerConfirms(false, null);

        assertThat(relay.publish(e)).isEqualTo(OutboxRelay.Outcome.RETRY);

        verify(outboxEventRepository).incrementRetry(e.getId(), OutboxRelay.MAX_RETRY);
        verify(outboxEventRepository, never()).markSent(any());
    }

    @Test
    @DisplayName("ACK nhưng bị return (không queue nào bind) -> incrementRetry, không markSent")
    void returned_incrementsRetry() {
        OutboxEvent e = event("PRODUCT_SKU_CREATED");
        ReturnedMessage returned = new ReturnedMessage(
                new Message(new byte[0]), 312, "NO_ROUTE", "product.events", "product.sku.created");
        brokerConfirms(true, returned);

        assertThat(relay.publish(e)).isEqualTo(OutboxRelay.Outcome.RETRY);

        verify(outboxEventRepository).incrementRetry(e.getId(), OutboxRelay.MAX_RETRY);
        verify(outboxEventRepository, never()).markSent(any());
    }

    @Test
    @DisplayName("Broker chết -> dừng batch, giữ PENDING, KHÔNG tăng retry")
    void brokerDown_stopsBatchWithoutBurningRetry() {
        OutboxEvent first = event("PRODUCT_SKU_CREATED");
        OutboxEvent second = event("PRODUCT_SKU_CREATED");
        List<UUID> ids = List.of(first.getId(), second.getId());
        when(outboxEventRepository.lockPendingIds(OutboxRelay.BATCH_SIZE)).thenReturn(ids);
        when(outboxEventRepository.findByIdInOrderByCreatedAtAsc(ids)).thenReturn(List.of(first, second));
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.relay();

        verify(rabbitTemplate, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        verify(outboxEventRepository, never()).incrementRetry(any(), anyInt());
        verify(outboxEventRepository, never()).markSent(any());
        // Trả lại cả lô chưa gửi để lượt sau gửi ngay, không phải chờ hết locked_until
        verify(outboxEventRepository).release(ids);
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
    @DisplayName("relay() nhận lô tối đa 50 event bằng SKIP LOCKED; không có event thì không claim")
    void relay_locksBatchOf50_noClaimWhenEmpty() {
        when(outboxEventRepository.lockPendingIds(50)).thenReturn(List.of());

        relay.relay();

        verify(outboxEventRepository).lockPendingIds(50);
        verify(outboxEventRepository, never()).claim(any(), anyInt());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("relay() đặt locked_until cho lô vừa nhận rồi gửi từng event theo thứ tự")
    void relay_claimsThenPublishes() {
        OutboxEvent first = event("PRODUCT_SKU_CREATED");
        OutboxEvent second = event("PRODUCT_SKU_CREATED");
        List<UUID> ids = List.of(first.getId(), second.getId());
        when(outboxEventRepository.lockPendingIds(50)).thenReturn(ids);
        when(outboxEventRepository.findByIdInOrderByCreatedAtAsc(ids)).thenReturn(List.of(first, second));
        brokerConfirms(true, null);

        relay.relay();

        verify(outboxEventRepository).claim(ids, OutboxRelay.CLAIM_SECONDS);
        verify(outboxEventRepository).markSent(first.getId());
        verify(outboxEventRepository).markSent(second.getId());
        verify(outboxEventRepository, never()).release(any());
    }
}
