package ra.edu.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ra.edu.paymentservice.entity.OutboxEvent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Copy từ order-service — dùng chung cho OutboxRelay của payment khi triển khai. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Đánh dấu SENT sau khi publish thành công — tránh SELECT rồi UPDATE riêng.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEvent e
            SET e.status      = 'SENT',
                e.publishedAt = CURRENT_TIMESTAMP
            WHERE e.id = :id
            """)
    int markSent(@Param("id") UUID id);

    /**
     * Tăng retry_count và chuyển sang FAILED nếu đã vượt ngưỡng.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEvent e
            SET e.retryCount = e.retryCount + 1,
                e.status     = CASE WHEN e.retryCount + 1 >= :maxRetry THEN 'FAILED' ELSE e.status END
            WHERE e.id = :id
            """)
    int incrementRetry(@Param("id") UUID id, @Param("maxRetry") int maxRetry);

    // ── Nhận việc an toàn khi chạy nhiều instance (FOR UPDATE SKIP LOCKED) ─────────────────
    // Bước 1 + 2 chạy trong MỘT transaction ngắn: khóa các dòng PENDING chưa ai nhận (dòng đang
    // bị instance khác khóa thì bỏ qua thay vì chờ), rồi đặt locked_until để sau khi commit các
    // instance khác vẫn bỏ qua chúng. Publish diễn ra NGOÀI transaction như cũ.
    // Instance chết giữa chừng: hết locked_until thì event tự được nhận lại -> không kẹt.

    /** Id các event PENDING chưa bị nhận (hoặc đã hết hạn nhận), cũ nhất trước. */
    @Query(value = """
            SELECT id FROM outbox_events
            WHERE status = 'PENDING'
              AND (locked_until IS NULL OR locked_until < now())
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> lockPendingIds(@Param("limit") int limit);

    /** Đánh dấu đã nhận trong :seconds giây. */
    @Modifying
    @Query(value = "UPDATE outbox_events SET locked_until = now() + (:seconds * interval '1 second') WHERE id IN (:ids)",
            nativeQuery = true)
    int claim(@Param("ids") Collection<UUID> ids, @Param("seconds") int seconds);

    /** Trả lại các event đã nhận nhưng chưa gửi (broker chết) để lượt sau gửi ngay. */
    @Modifying
    @Query(value = "UPDATE outbox_events SET locked_until = NULL WHERE id IN (:ids)", nativeQuery = true)
    int release(@Param("ids") Collection<UUID> ids);

    List<OutboxEvent> findByIdInOrderByCreatedAtAsc(Collection<UUID> ids);
}
