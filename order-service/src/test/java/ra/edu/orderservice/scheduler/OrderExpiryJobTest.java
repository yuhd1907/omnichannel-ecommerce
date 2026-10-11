package ra.edu.orderservice.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import ra.edu.orderservice.repository.OrderRepository;
import ra.edu.orderservice.service.OrderStateService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpiryJobTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStateService orderStateService;

    private OrderExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new OrderExpiryJob(orderRepository, orderStateService, 15);
    }

    @Test
    void expiresAllReturnedOrders() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(orderRepository.findExpiredPendingIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(id1, id2));
        when(orderStateService.cancelAndRelease(any(UUID.class), eq("UNPAID"), eq("EXPIRED")))
                .thenReturn(true);

        job.expireOverdueOrders();

        verify(orderStateService).cancelAndRelease(id1, "UNPAID", "EXPIRED");
        verify(orderStateService).cancelAndRelease(id2, "UNPAID", "EXPIRED");
    }

    @Test
    void continuesWhenOneOrderFails() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(orderRepository.findExpiredPendingIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(id1, id2));
        when(orderStateService.cancelAndRelease(eq(id1), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB error"));
        when(orderStateService.cancelAndRelease(eq(id2), anyString(), anyString()))
                .thenReturn(true);

        job.expireOverdueOrders();

        verify(orderStateService).cancelAndRelease(id2, "UNPAID", "EXPIRED");
    }

    @Test
    void doesNothingWhenNoExpiredOrders() {
        when(orderRepository.findExpiredPendingIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        job.expireOverdueOrders();

        verifyNoInteractions(orderStateService);
    }

    @Test
    void usesTtlForCutoff() {
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(orderRepository.findExpiredPendingIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        Instant before = Instant.now().minus(Duration.ofMinutes(15));
        job.expireOverdueOrders();
        Instant after = Instant.now().minus(Duration.ofMinutes(15));

        verify(orderRepository).findExpiredPendingIds(cutoffCaptor.capture(), pageableCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isBetween(before.minusSeconds(1), after.plusSeconds(1));
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }
}
