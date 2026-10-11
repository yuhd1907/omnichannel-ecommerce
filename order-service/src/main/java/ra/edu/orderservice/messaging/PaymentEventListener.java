package ra.edu.orderservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ra.edu.orderservice.service.OrderStateService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consumer của queue order.payment-events (kết quả thanh toán từ payment-service).
 *
 * Không cần bảng chống trùng: OrderStateService chỉ tác động khi đơn còn PENDING, nên nhận lại
 * event cũ (outbox at-least-once) không làm gì thêm.
 *
 * Message hỏng (JSON sai, thiếu orderId) -> AmqpRejectAndDontRequeueException: vào DLQ ngay.
 * Lỗi tạm thời (DB...) -> exception thường: retry có backoff rồi mới DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    static final String PAYMENT_FAILED    = "PAYMENT_FAILED";

    private final OrderStateService orderStateService;
    private final JsonMapper        jsonMapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentResultPayload(UUID paymentId, UUID orderId, BigDecimal amount, String gatewayTxnId) {}

    @RabbitListener(queues = RabbitTopologyConfig.PAYMENT_QUEUE)
    public void onPaymentEvent(Message message) {
        MessageProperties props = message.getMessageProperties();
        Object header = props.getHeader("eventType");
        String eventType = header == null ? null : header.toString();

        if (!PAYMENT_SUCCEEDED.equals(eventType) && !PAYMENT_FAILED.equals(eventType)) {
            log.info("Ignore payment event with unsupported eventType={} (messageId={}, routingKey={})",
                    eventType, props.getMessageId(), props.getReceivedRoutingKey());
            return;
        }

        PaymentResultPayload payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), PaymentResultPayload.class);
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException(
                    "Malformed payment event payload (messageId=" + props.getMessageId() + ")", e);
        }
        if (payload == null || payload.orderId() == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "Payment event missing orderId (messageId=" + props.getMessageId() + ")");
        }

        if (PAYMENT_SUCCEEDED.equals(eventType)) {
            orderStateService.confirmPaid(payload.orderId());
        } else {
            orderStateService.cancelAndRelease(payload.orderId(), "FAILED", "PAYMENT_FAILED");
        }
    }
}
