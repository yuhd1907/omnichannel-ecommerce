package ra.edu.orderservice.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ra.edu.orderservice.repository.OrderRepository;
import ra.edu.orderservice.service.OrderStateService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class OrderExpiryJob {

    static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;
    private final long ttlMinutes;

    public OrderExpiryJob(OrderRepository orderRepository,
                          OrderStateService orderStateService,
                          @Value("${app.inventory.reservation-ttl-minutes:15}") long ttlMinutes) {
        this.orderRepository = orderRepository;
        this.orderStateService = orderStateService;
        this.ttlMinutes = ttlMinutes;
    }

    @Scheduled(fixedDelayString = "${app.inventory.expiry-check-interval-ms:60000}", initialDelay = 30000)
    public void expireOverdueOrders() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(ttlMinutes));
        List<UUID> ids = orderRepository.findExpiredPendingIds(cutoff, Pageable.ofSize(BATCH_SIZE));
        if (ids.isEmpty()) {
            return;
        }

        int expired = 0;
        for (UUID id : ids) {
            try {
                if (orderStateService.cancelAndRelease(id, "UNPAID", "EXPIRED")) {
                    expired++;
                }
            } catch (Exception e) {
                log.error("Expire order failed, sẽ thử lại lượt sau (orderId={})", id, e);
            }
        }

        log.info("Order expiry: {} / {} orders expired (cutoff={})", expired, ids.size(), cutoff);
    }
}
