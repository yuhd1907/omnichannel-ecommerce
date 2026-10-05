package ra.edu.orderservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ra.edu.orderservice.entity.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Lấy batch event PENDING để publish (dùng index idx_outbox_events_status_created_at).
     * Pageable.ofSize(n) để giới hạn batch size.
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

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
}
