package ra.edu.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ra.edu.orderservice.entity.Order;
import ra.edu.orderservice.entity.OrderItem;
import ra.edu.orderservice.entity.OutboxEvent;
import ra.edu.orderservice.exception.BusinessException;
import ra.edu.orderservice.repository.InventoryRepository;
import ra.edu.orderservice.repository.OrderRepository;
import ra.edu.orderservice.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStateServiceTest {

    @Mock private OrderRepository       orderRepository;
    @Mock private InventoryRepository   inventoryRepository;
    @Mock private OutboxEventRepository outboxEventRepository;

    private OrderStateService service;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderStateService(orderRepository, inventoryRepository, outboxEventRepository,
                JsonMapper.builder().build());
    }

    private Order orderWithTwoItems() {
        Order order = Order.builder().id(orderId).userId(userId).channel("WEB")
                .totalAmount(new BigDecimal("300000.00")).build();
        order.setItems(List.of(
                OrderItem.builder().order(order).skuCode("SKU-A").quantity(1L).build(),
                OrderItem.builder().order(order).skuCode("SKU-B").quantity(2L).build()));
        return order;
    }

    // ─────────────────────────── cancelAndRelease ──────────────────────────

    @Test
    @DisplayName("cancelAndRelease: thua race (đơn không còn PENDING) -> false, KHÔNG chạm kho, không outbox")
    void cancel_notPending_doesNothing() {
        when(orderRepository.transitionFromPending(orderId, "CANCELLED", "UNPAID")).thenReturn(0);

        assertThat(service.cancelAndRelease(orderId, "UNPAID", "USER_CANCELLED")).isFalse();

        verifyNoInteractions(inventoryRepository, outboxEventRepository);
        verify(orderRepository, never()).findWithItemsById(any());
    }

    @Test
    @DisplayName("cancelAndRelease: chuyển trạng thái TRƯỚC, rồi release từng item, outbox ORDER_CANCELLED có reason")
    void cancel_success_transitionsThenReleases() {
        when(orderRepository.transitionFromPending(orderId, "CANCELLED", "FAILED")).thenReturn(1);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(orderWithTwoItems()));
        when(inventoryRepository.release(any(), anyLong())).thenReturn(1);

        assertThat(service.cancelAndRelease(orderId, "FAILED", "PAYMENT_FAILED")).isTrue();

        InOrder inOrder = inOrder(orderRepository, inventoryRepository);
        inOrder.verify(orderRepository).transitionFromPending(orderId, "CANCELLED", "FAILED");
        inOrder.verify(inventoryRepository).release("SKU-A", 1L);
        inOrder.verify(inventoryRepository).release("SKU-B", 2L);

        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("ORDER_CANCELLED");
        assertThat(ev.getValue().getPayload())
                .contains("\"orderId\":\"" + orderId + "\"", "\"userId\":\"" + userId + "\"", "\"reason\":\"PAYMENT_FAILED\"");
    }

    @Test
    @DisplayName("cancelAndRelease: release trả 0 -> ném lỗi để rollback toàn bộ, không outbox")
    void cancel_releaseFails_throws() {
        when(orderRepository.transitionFromPending(any(), any(), any())).thenReturn(1);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(orderWithTwoItems()));
        when(inventoryRepository.release("SKU-A", 1L)).thenReturn(0);

        assertThatThrownBy(() -> service.cancelAndRelease(orderId, "UNPAID", "USER_CANCELLED"))
                .isInstanceOf(BusinessException.class);
        verify(outboxEventRepository, never()).save(any());
    }

    // ─────────────────────────── confirmPaid ──────────────────────────

    @Test
    @DisplayName("confirmPaid: đơn đã hủy (không còn PENDING) -> false, KHÔNG confirm kho")
    void confirm_notPending_doesNothing() {
        when(orderRepository.transitionFromPending(orderId, "CONFIRMED", "PAID")).thenReturn(0);

        assertThat(service.confirmPaid(orderId)).isFalse();

        verifyNoInteractions(inventoryRepository, outboxEventRepository);
    }

    @Test
    @DisplayName("confirmPaid: CONFIRMED/PAID, confirm từng item, outbox ORDER_CONFIRMED có totalAmount")
    void confirm_success() {
        when(orderRepository.transitionFromPending(orderId, "CONFIRMED", "PAID")).thenReturn(1);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(orderWithTwoItems()));
        when(inventoryRepository.confirm(any(), anyLong())).thenReturn(1);

        assertThat(service.confirmPaid(orderId)).isTrue();

        verify(inventoryRepository).confirm("SKU-A", 1L);
        verify(inventoryRepository).confirm("SKU-B", 2L);
        verify(inventoryRepository, never()).release(any(), anyLong());
        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(ev.getValue().getPayload()).contains("\"totalAmount\":300000.00");
    }

    @Test
    @DisplayName("confirmPaid: confirm kho trả 0 -> ném lỗi để rollback")
    void confirm_inventoryFails_throws() {
        when(orderRepository.transitionFromPending(any(), any(), any())).thenReturn(1);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(orderWithTwoItems()));
        when(inventoryRepository.confirm("SKU-A", 1L)).thenReturn(0);

        assertThatThrownBy(() -> service.confirmPaid(orderId)).isInstanceOf(BusinessException.class);
        verify(outboxEventRepository, never()).save(any());
    }

    // ─────────────────────────── advanceFulfillment ──────────────────────────

    @Test
    @DisplayName("advanceFulfillment: CONFIRMED -> SHIPPING, outbox ORDER_SHIPPED, không chạm kho")
    void advance_toShipping() {
        when(orderRepository.transition(orderId, "CONFIRMED", "SHIPPING")).thenReturn(1);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(orderWithTwoItems()));

        service.advanceFulfillment(orderId, "shipping");

        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("ORDER_SHIPPED");
        assertThat(ev.getValue().getPayload()).contains("\"orderId\":\"" + orderId + "\"");
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("advanceFulfillment: SHIPPING -> COMPLETED, outbox ORDER_COMPLETED")
    void advance_toCompleted() {
        when(orderRepository.transition(orderId, "SHIPPING", "COMPLETED")).thenReturn(1);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(orderWithTwoItems()));

        service.advanceFulfillment(orderId, "COMPLETED");

        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("ORDER_COMPLETED");
    }

    @Test
    @DisplayName("advanceFulfillment: đơn PENDING mà đòi COMPLETED -> 409, không outbox")
    void advance_wrongState_conflict() {
        when(orderRepository.transition(orderId, "SHIPPING", "COMPLETED")).thenReturn(0);
        Order pending = orderWithTwoItems();
        pending.setStatus("PENDING");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.advanceFulfillment(orderId, "COMPLETED"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("advanceFulfillment: không có đơn -> 404")
    void advance_notFound() {
        when(orderRepository.transition(orderId, "CONFIRMED", "SHIPPING")).thenReturn(0);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advanceFulfillment(orderId, "SHIPPING"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("advanceFulfillment: target không hợp lệ (CANCELLED/PENDING) -> 400, không gọi DB")
    void advance_invalidTarget() {
        assertThatThrownBy(() -> service.advanceFulfillment(orderId, "CANCELLED"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(orderRepository, outboxEventRepository);
    }
}
