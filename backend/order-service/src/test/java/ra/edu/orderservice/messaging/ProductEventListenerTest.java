package ra.edu.orderservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ra.edu.orderservice.repository.InventoryRepository;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductEventListenerTest {

    @Mock
    private InventoryRepository inventoryRepository;

    private ProductEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ProductEventListener(inventoryRepository, JsonMapper.builder().build());
    }

    private static Message message(String eventType, String body) {
        MessageProperties p = new MessageProperties();
        p.setMessageId("msg-1");
        if (eventType != null) {
            p.setHeader("eventType", eventType);
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), p);
    }

    @Test
    @DisplayName("PRODUCT_SKU_CREATED -> tạo dòng kho rỗng cho SKU (bỏ qua field lạ)")
    void skuCreated_createsEmptyInventoryRow() {
        when(inventoryRepository.createEmptyIfAbsent("NEW-SKU-01")).thenReturn(1);

        listener.onProductEvent(message("PRODUCT_SKU_CREATED",
                "{\"skuCode\":\"NEW-SKU-01\",\"productId\":\"p-1\",\"productName\":\"Ao\",\"price\":100000}"));

        verify(inventoryRepository).createEmptyIfAbsent("NEW-SKU-01");
    }

    @Test
    @DisplayName("Nhận lại event cũ / SKU đã có kho -> không lỗi (ON CONFLICT DO NOTHING trả 0)")
    void duplicate_isNoOp() {
        when(inventoryRepository.createEmptyIfAbsent("NEW-SKU-01")).thenReturn(0);

        listener.onProductEvent(message("PRODUCT_SKU_CREATED", "{\"skuCode\":\"NEW-SKU-01\"}"));

        verify(inventoryRepository).createEmptyIfAbsent("NEW-SKU-01");
    }

    @Test
    @DisplayName("eventType khác -> bỏ qua")
    void otherEventType_ignored() {
        listener.onProductEvent(message("PRODUCT_UPDATED", "{\"skuCode\":\"X\"}"));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("JSON hỏng hoặc thiếu skuCode -> reject không requeue (vào DLQ)")
    void malformed_rejected() {
        assertThatThrownBy(() -> listener.onProductEvent(message("PRODUCT_SKU_CREATED", "not-json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onProductEvent(message("PRODUCT_SKU_CREATED", "{\"price\":1}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(inventoryRepository);
    }
}
