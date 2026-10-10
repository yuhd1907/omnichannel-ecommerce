package ra.edu.notificationservice.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Topology phía consumer (xem docs/Messaging_Topology.md): bên nhận khai báo queue + binding + DLQ.
 *
 * Khai báo lại exchange order.events (cùng tham số với order-service) để binding không lỗi
 * khi notification khởi động trước bên phát — RabbitMQ chấp nhận declare lặp nếu tham số giống.
 *
 * DLX là fanout riêng cho queue này: message bị dead-letter GIỮ routing key gốc (order.created);
 * với direct DLX bind bằng key khác, message sẽ bị drop im lặng.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String RK_ORDER_CREATED      = "order.created";
    public static final String RK_ORDER_CANCELLED    = "order.cancelled";

    public static final String QUEUE = "notification.order-events";
    public static final String DLX   = QUEUE + ".dlx";
    public static final String DLQ   = QUEUE + ".dlq";

    @Bean
    public TopicExchange orderEventsExchange() {
        return ExchangeBuilder.topicExchange(ORDER_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue notificationOrderEventsQueue() {
        // Tham số queue KHÔNG đổi được sau khi tạo (PRECONDITION_FAILED) -> DLX phải có từ đầu.
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).build();
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(notificationOrderEventsQueue()).to(orderEventsExchange()).with(RK_ORDER_CREATED);
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder.bind(notificationOrderEventsQueue()).to(orderEventsExchange()).with(RK_ORDER_CANCELLED);
    }

    @Bean
    public FanoutExchange notificationOrderEventsDlx() {
        return ExchangeBuilder.fanoutExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue notificationOrderEventsDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(notificationOrderEventsDlq()).to(notificationOrderEventsDlx());
    }

    /**
     * Retry interceptor của Spring AMQP 4 retry MỌI exception, kể cả AmqpRejectAndDontRequeueException.
     * Loại trừ nó để message hỏng vào DLQ ngay, không tốn 3 lần thử.
     * (Policy khớp excludes theo cả chuỗi cause, nên ListenerExecutionFailedException bọc ngoài vẫn khớp.)
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer skipRetryForRejectedMessages() {
        return settings -> settings.setExceptionExcludes(List.of(AmqpRejectAndDontRequeueException.class));
    }
}
