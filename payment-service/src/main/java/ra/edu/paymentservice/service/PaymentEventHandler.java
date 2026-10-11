package ra.edu.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.paymentservice.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final PaymentRepository paymentRepository;

    /**
     * Xử lý event order.created:
     * Idempotent qua ON CONFLICT (order_id) DO NOTHING.
     */
    @Transactional
    public void handleOrderCreated(UUID orderId, UUID userId, BigDecimal amount) {
        log.info("Processing order.created event for orderId={}, userId={}, amount={}", orderId, userId, amount);
        int rows = paymentRepository.insertPendingIfAbsent(orderId, userId, amount);
        if (rows > 0) {
            log.info("Created PENDING payment for orderId={}", orderId);
        } else {
            log.info("Payment already exists for orderId={}, skipping duplicate event", orderId);
        }
    }

    /**
     * Xử lý event order.cancelled:
     * Idempotent qua UPDATE ... WHERE status = 'PENDING'.
     */
    @Transactional
    public void handleOrderCancelled(UUID orderId) {
        log.info("Processing order.cancelled event for orderId={}", orderId);
        int rows = paymentRepository.failIfPending(orderId);
        if (rows > 0) {
            log.info("Payment for orderId={} transitioned to FAILED", orderId);
        } else {
            log.info("Payment for orderId={} was not in PENDING state (or not found), skipping cancel", orderId);
        }
    }
}
