package ra.edu.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ra.edu.paymentservice.entity.PaymentTransaction;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    /**
     * Ghi giao dịch từ webhook. Trả 1 nếu là lần đầu, 0 nếu gateway_txn_id đã có (webhook lặp).
     *
     * ON CONFLICT DO NOTHING thay vì bắt unique violation: trên PostgreSQL lỗi làm transaction
     * chuyển sang aborted -> không thể đi tiếp hay commit. Hai webhook cùng txn đến đồng thời:
     * bên sau chờ lock của bên trước rồi nhận 0.
     */
    @Modifying
    @Query(value = """
            INSERT INTO payment_transactions (id, payment_id, gateway_txn_id, status, amount, raw_response, created_at)
            VALUES (gen_random_uuid(), :paymentId, :gatewayTxnId, :status, :amount, CAST(:raw AS jsonb), now())
            ON CONFLICT (gateway_txn_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("paymentId") UUID paymentId,
                       @Param("gatewayTxnId") String gatewayTxnId,
                       @Param("status") String status,
                       @Param("amount") BigDecimal amount,
                       @Param("raw") String raw);
}
