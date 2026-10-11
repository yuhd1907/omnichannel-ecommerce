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
}
