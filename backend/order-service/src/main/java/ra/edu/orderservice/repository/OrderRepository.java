package ra.edu.orderservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ra.edu.orderservice.entity.Order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("""
            SELECT o.id FROM Order o
            WHERE o.status = 'PENDING' AND o.createdAt < :cutoff
            ORDER BY o.createdAt
            """)
    List<UUID> findExpiredPendingIds(@Param("cutoff") Instant cutoff, Pageable pageable);

    /** Danh sách đơn của một user, dùng index idx_orders_user_created_at. */
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    /** Lấy đơn kèm items trong 1 query (tránh N+1). */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    /**
     * Chuyển trạng thái CHỈ khi đơn còn PENDING. Trả 1 nếu thắng, 0 nếu đơn đã rời PENDING.
     *
     * Đây là chốt chống race "đọc PENDING trong Java rồi setStatus": hủy đơn và thanh toán
     * thành công đến cùng lúc thì UPDATE thứ hai chờ khóa dòng, đọc lại status sau khi bên đầu
     * commit và nhận 0 -> chỉ một nhánh được tác động lên kho.
     *
     * clearAutomatically: UPDATE dạng này bỏ qua persistence context; không clear thì entity Order
     * đã nạp trước đó vẫn mang status cũ và lần save sau có thể ghi ngược lại.
     * flushAutomatically: ghi các thay đổi đang chờ xuống DB TRƯỚC khi clear, nếu không sẽ mất.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Order o
            SET o.status        = :status,
                o.paymentStatus = :paymentStatus,
                o.updatedAt     = CURRENT_TIMESTAMP
            WHERE o.id = :id
              AND o.status = 'PENDING'
            """)
    int transitionFromPending(@Param("id") UUID orderId,
                              @Param("status") String newStatus,
                              @Param("paymentStatus") String newPaymentStatus);

    /** Ghi phương thức thanh toán đã dùng (gọi ngay sau transitionFromPending thắng, cùng transaction). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.paymentMethod = :method WHERE o.id = :id")
    int setPaymentMethod(@Param("id") UUID orderId, @Param("method") String method);

    /** Đơn COD giao xong: shipper đã thu tiền mặt -> UNPAID thành PAID. Đơn đã trả online không đổi. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.paymentStatus = 'PAID', o.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE o.id = :id AND o.paymentStatus = 'UNPAID'")
    int markPaidIfUnpaid(@Param("id") UUID orderId);

    /**
     * Chuyển trạng thái fulfillment (CONFIRMED -> SHIPPING -> COMPLETED) có điều kiện.
     * Cùng nguyên tắc với transitionFromPending: chỉ một request thắng, request còn lại nhận 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Order o
            SET o.status    = :to,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.id = :id
              AND o.status = :from
            """)
    int transition(@Param("id") UUID orderId,
                   @Param("from") String fromStatus,
                   @Param("to") String toStatus);

    /** Danh sách đơn theo trạng thái (admin). */
    Page<Order> findByStatus(String status, Pageable pageable);

    // ── Idempotency-Key cho POST /orders (bảng idempotency_keys, migration V3) ──────────────

    /** Bản ghi key đã dùng: nội dung đơn (hash) và đơn đã tạo. */
    interface IdempotencyRecord {
        String getRequestHash();
        UUID getOrderId();
    }

    @Query(value = """
            SELECT request_hash AS "requestHash", order_id AS "orderId"
            FROM idempotency_keys
            WHERE user_id = :userId AND idem_key = :key
            """, nativeQuery = true)
    Optional<IdempotencyRecord> findIdempotencyRecord(@Param("userId") UUID userId, @Param("key") String key);

    /**
     * Ghi key CÙNG transaction với đơn. Hai request trùng key chạy song song: request sau chờ
     * khóa của khóa chính tới khi request trước commit, rồi nhận 0 -> rollback đơn của mình và
     * trả lại đơn của request trước.
     *
     * @return 1 nếu ghi được, 0 nếu key đã tồn tại
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (user_id, idem_key, request_hash, order_id)
            VALUES (:userId, :key, :hash, :orderId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIdempotencyKey(@Param("userId") UUID userId, @Param("key") String key,
                             @Param("hash") String requestHash, @Param("orderId") UUID orderId);
}
