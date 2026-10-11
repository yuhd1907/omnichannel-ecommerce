package ra.edu.paymentservice.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Topology RabbitMQ cho payment-service (xem docs/Messaging_Topology.md).
 *
 * Khai báo lại exchange order.events (cùng tham số với order-service) để binding không lỗi
 * khi payment khởi động trước bên phát — RabbitMQ chấp nhận declare lặp nếu tham số giống.
 * Khai báo exchange mới payment.events.
 *
 * DLX là fanout riêng cho queue này: message bị dead-letter GIỮ routing key gốc;
 * với direct DLX bind bằng key khác, message sẽ bị drop im lặng.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String ORDER_EVENTS_EXCHANGE   = "order.events";
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";

    public static final String RK_ORDER_CREATED   = "order.created";
    public static final String RK_ORDER_CANCELLED = "order.cancelled";

    public static final String QUEUE = "payment.order-events";
    public static final String DLX   = QUEUE + ".dlx";
    public static final String DLQ   = QUEUE + ".dlq";

    @Bean
    public TopicExchange orderEventsExchange() {
        return ExchangeBuilder.topicExchange(ORDER_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return ExchangeBuilder.topicExchange(PAYMENT_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue paymentOrderEventsQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).build();
    }

    @Bean
    public Binding paymentOrderCreatedBinding() {
        return BindingBuilder.bind(paymentOrderEventsQueue()).to(orderEventsExchange()).with(RK_ORDER_CREATED);
    }

    @Bean
    public Binding paymentOrderCancelledBinding() {
        return BindingBuilder.bind(paymentOrderEventsQueue()).to(orderEventsExchange()).with(RK_ORDER_CANCELLED);
    }

    @Bean
    public FanoutExchange paymentOrderEventsDlx() {
        return ExchangeBuilder.fanoutExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue paymentOrderEventsDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding paymentDlqBinding() {
        return BindingBuilder.bind(paymentOrderEventsDlq()).to(paymentOrderEventsDlx());
    }

    /**
     * Loại trừ AmqpRejectAndDontRequeueException khỏi retry policy
     * để message hỏng bay thẳng vào DLQ ngay mà không retry.
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer skipRetryForRejectedMessages() {
        return settings -> settings.setExceptionExcludes(List.of(AmqpRejectAndDontRequeueException.class));
    }
}
