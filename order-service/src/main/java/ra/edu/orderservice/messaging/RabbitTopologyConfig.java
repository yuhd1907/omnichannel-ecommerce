package ra.edu.orderservice.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Topology RabbitMQ phía order-service (xem docs/Messaging_Topology.md).
 *
 * Bên phát: exchange order.events. Bên nhận: queue order.payment-events (+ DLX/DLQ) bind vào
 * payment.events. Khai báo lặp exchange payment.events an toàn vì tham số giống payment-service.
 *
 * DLX là fanout riêng cho queue: message bị dead-letter GIỮ routing key gốc (payment.succeeded);
 * với direct DLX bind bằng key khác, message sẽ bị drop im lặng.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String ORDER_EVENTS_EXCHANGE   = "order.events";
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";

    public static final String RK_ORDER_CREATED   = "order.created";
    public static final String RK_ORDER_CANCELLED = "order.cancelled";
    public static final String RK_ORDER_CONFIRMED = "order.confirmed";
    public static final String RK_ORDER_SHIPPED   = "order.shipped";
    public static final String RK_ORDER_COMPLETED = "order.completed";

    public static final String RK_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String RK_PAYMENT_FAILED    = "payment.failed";

    public static final String PAYMENT_QUEUE = "order.payment-events";
    public static final String PAYMENT_DLX   = PAYMENT_QUEUE + ".dlx";
    public static final String PAYMENT_DLQ   = PAYMENT_QUEUE + ".dlq";

    @Bean
    public TopicExchange orderEventsExchange() {
        return ExchangeBuilder.topicExchange(ORDER_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return ExchangeBuilder.topicExchange(PAYMENT_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue orderPaymentEventsQueue() {
        // Tham số queue KHÔNG đổi được sau khi tạo (PRECONDITION_FAILED) -> DLX phải có từ đầu.
        return QueueBuilder.durable(PAYMENT_QUEUE).deadLetterExchange(PAYMENT_DLX).build();
    }

    @Bean
    public Binding paymentSucceededBinding() {
        return BindingBuilder.bind(orderPaymentEventsQueue()).to(paymentEventsExchange()).with(RK_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(orderPaymentEventsQueue()).to(paymentEventsExchange()).with(RK_PAYMENT_FAILED);
    }

    @Bean
    public FanoutExchange orderPaymentEventsDlx() {
        return ExchangeBuilder.fanoutExchange(PAYMENT_DLX).durable(true).build();
    }

    @Bean
    public Queue orderPaymentEventsDlq() {
        return QueueBuilder.durable(PAYMENT_DLQ).build();
    }

    @Bean
    public Binding orderPaymentEventsDlqBinding() {
        return BindingBuilder.bind(orderPaymentEventsDlq()).to(orderPaymentEventsDlx());
    }

    /**
     * Retry interceptor của Spring AMQP 4 retry MỌI exception, kể cả AmqpRejectAndDontRequeueException.
     * Loại trừ nó để message hỏng vào DLQ ngay, không tốn 3 lần thử.
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer skipRetryForRejectedMessages() {
        return settings -> settings.setExceptionExcludes(List.of(AmqpRejectAndDontRequeueException.class));
    }
}
