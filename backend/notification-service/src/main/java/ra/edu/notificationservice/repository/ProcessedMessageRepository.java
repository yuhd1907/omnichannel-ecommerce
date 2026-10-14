package ra.edu.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ra.edu.notificationservice.entity.ProcessedMessage;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    /**
     * Ghi nhận message đã xử lý. Trả 1 nếu là lần đầu, 0 nếu đã có (message trùng).
     *
     * Dùng ON CONFLICT DO NOTHING thay vì bắt unique violation: trên PostgreSQL, lỗi bất kỳ
     * làm transaction chuyển sang aborted và Spring đánh dấu rollback-only -> bắt exception
     * rồi return vẫn nổ UnexpectedRollbackException khi commit, message trùng bị retry/DLQ.
     * Hai consumer cùng insert một message_id: bên sau chờ lock của bên trước, rồi nhận 0.
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_messages (message_id, processed_at)
            VALUES (:messageId, CURRENT_TIMESTAMP)
            ON CONFLICT (message_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("messageId") String messageId);
}
