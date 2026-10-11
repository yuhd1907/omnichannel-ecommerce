package ra.edu.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ra.edu.paymentservice.entity.Payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    @Modifying
    @Query(value = """
            INSERT INTO payments (id, order_id, user_id, amount, status, needs_refund, created_at, updated_at)
            VALUES (gen_random_uuid(), :orderId, :userId, :amount, 'PENDING', false, now(), now())
            ON CONFLICT (order_id) DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(@Param("orderId") UUID orderId,
                              @Param("userId") UUID userId,
                              @Param("amount") BigDecimal amount);

    @Modifying
    @Query(value = """
            UPDATE payments
            SET status = 'FAILED', updated_at = now()
            WHERE order_id = :orderId AND status = 'PENDING'
            """, nativeQuery = true)
    int failIfPending(@Param("orderId") UUID orderId);

    /**
     * Ghi kết quả webhook. Chỉ đổi được khi còn PENDING: trả 1 nếu thắng, 0 nếu payment đã
     * SUCCESS/FAILED (webhook đến muộn, đơn đã hủy...). Điều kiện status='PENDING' + khóa dòng
     * của UPDATE là thứ chống hai kết quả khác nhau cùng ghi đè nhau.
     */
    @Modifying
    @Query(value = """
            UPDATE payments
            SET status = :status, updated_at = now()
            WHERE order_id = :orderId AND status = 'PENDING'
            """, nativeQuery = true)
    int markResultIfPending(@Param("orderId") UUID orderId, @Param("status") String status);

    /** Tiền đã thu nhưng payment không còn PENDING -> cần đối soát / hoàn tiền thủ công. */
    @Modifying
    @Query(value = """
            UPDATE payments
            SET needs_refund = true, updated_at = now()
            WHERE order_id = :orderId
            """, nativeQuery = true)
    int markNeedsRefund(@Param("orderId") UUID orderId);
}
