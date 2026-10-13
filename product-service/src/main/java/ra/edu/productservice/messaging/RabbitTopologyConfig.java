package ra.edu.productservice.messaging;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology RabbitMQ phía product-service (xem docs/Messaging_Topology.md).
 *
 * product-service chỉ PHÁT event, không nhận -> chỉ khai báo exchange product.events.
 * Queue nhận (order.product-events + DLX/DLQ) do bên nhận (order-service) tự khai báo.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String PRODUCT_EVENTS_EXCHANGE = "product.events";

    public static final String RK_SKU_CREATED = "product.sku.created";

    @Bean
    public TopicExchange productEventsExchange() {
        return ExchangeBuilder.topicExchange(PRODUCT_EVENTS_EXCHANGE).durable(true).build();
    }
}
