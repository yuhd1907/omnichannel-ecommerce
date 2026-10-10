package ra.edu.orderservice.messaging;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology RabbitMQ phía order-service (xem docs/Messaging_Topology.md).
 *
 * Bên phát khai báo exchange; RabbitAdmin tự declare khi mở kết nối đầu tiên.
 * Khai báo lặp ở consumer an toàn vì tham số giống hệt (topic, durable).
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";

    public static final String RK_ORDER_CREATED   = "order.created";
    public static final String RK_ORDER_CANCELLED = "order.cancelled";

    @Bean
    public TopicExchange orderEventsExchange() {
        return ExchangeBuilder.topicExchange(ORDER_EVENTS_EXCHANGE).durable(true).build();
    }
}
