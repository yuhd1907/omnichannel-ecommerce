package ra.edu.orderservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.orderservice.repository.InventoryRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumer của queue order.product-events: product-service tạo SKU mới -> tạo sẵn dòng kho
 * available_qty = 0 để admin chỉ cần nhập số lượng (POST /admin/inventory/{sku}/adjustments).
 *
 * Idempotent nhờ INSERT ... ON CONFLICT (sku_code) DO NOTHING: nhận lại event (outbox
 * at-least-once) hoặc SKU đã được admin nhập kho trước đó đều không đổi dữ liệu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    static final String PRODUCT_SKU_CREATED = "PRODUCT_SKU_CREATED";

    private final InventoryRepository inventoryRepository;
    private final JsonMapper          jsonMapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SkuCreatedPayload(String skuCode) {}

    @RabbitListener(queues = RabbitTopologyConfig.PRODUCT_QUEUE)
    @Transactional
    public void onProductEvent(Message message) {
        MessageProperties props = message.getMessageProperties();
        Object header = props.getHeader("eventType");
        String eventType = header == null ? null : header.toString();

        if (!PRODUCT_SKU_CREATED.equals(eventType)) {
            log.info("Ignore product event with unsupported eventType={} (messageId={})", eventType, props.getMessageId());
            return;
        }

        SkuCreatedPayload payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), SkuCreatedPayload.class);
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException(
                    "Malformed product event payload (messageId=" + props.getMessageId() + ")", e);
        }
        if (payload == null || payload.skuCode() == null || payload.skuCode().isBlank()) {
            throw new AmqpRejectAndDontRequeueException(
                    "Product event missing skuCode (messageId=" + props.getMessageId() + ")");
        }

        if (inventoryRepository.createEmptyIfAbsent(payload.skuCode()) == 1) {
            log.info("Inventory row created for new SKU {} (available_qty=0)", payload.skuCode());
        } else {
            log.debug("Inventory row already exists for SKU {}, skip", payload.skuCode());
        }
    }
}
