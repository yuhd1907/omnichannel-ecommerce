package ra.edu.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.Optional;
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
     *
     * Không còn PENDING mà đang SUCCESS = webhook SUCCESS tới payment TRƯỚC order.cancelled:
     * tiền đã thu nhưng order đã hủy (và bỏ qua payment.succeeded) -> đánh dấu needs_refund để
     * admin hoàn tiền. Không cần event mới: order.cancelled đã tới đây sẵn.
     */
    @Transactional
    public void handleOrderCancelled(UUID orderId) {
        log.info("Processing order.cancelled event for orderId={}", orderId);
        int rows = paymentRepository.failIfPending(orderId);
        if (rows > 0) {
            log.info("Payment for orderId={} transitioned to FAILED", orderId);
            return;
        }

        Optional<Payment> payment = paymentRepository.findByOrderId(orderId);
        if (payment.isPresent() && "SUCCESS".equals(payment.get().getStatus())) {
            paymentRepository.markNeedsRefund(orderId);
            log.error("ORDER CANCELLED AFTER PAYMENT SUCCESS: money captured for cancelled order -> needs_refund=true "
                    + "(orderId={}, paymentId={}, amount={})",
                    orderId, payment.get().getId(), payment.get().getAmount());
        } else {
            log.info("Payment for orderId={} was not in PENDING state (or not found), skipping cancel", orderId);
        }
    }
}
