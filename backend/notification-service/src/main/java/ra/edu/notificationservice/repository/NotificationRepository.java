package ra.edu.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.notificationservice.entity.Notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * Các UPDATE trạng thái email dùng REQUIRES_NEW: EmailDispatcher được gọi trong afterCommit của
 * transaction lưu notification; khi đó REQUIRED sẽ "nhập" vào transaction ĐÃ commit và thay đổi
 * không bao giờ được ghi (thư kẹt ở PENDING tới lượt quét sau).
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Email đến hạn gửi (mới tạo hoặc chờ gửi lại), cũ trước. */
    @Query(value = """
            SELECT id FROM notifications
            WHERE email_status = 'PENDING' AND email_next_attempt_at <= :now
            ORDER BY email_next_attempt_at
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findDueEmailIds(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * Giữ chỗ trước khi gửi: đẩy hạn gửi ra sau (lease). Chỉ một luồng/instance nhận được 1,
     * nên không gửi trùng; tiến trình chết giữa chừng thì hết lease thư tự được gửi lại.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            UPDATE notifications SET email_next_attempt_at = :leaseUntil
            WHERE id = :id AND email_status = 'PENDING' AND email_next_attempt_at <= :now
            """, nativeQuery = true)
    int claimEmail(@Param("id") UUID id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            UPDATE notifications
            SET email_status = 'SENT', email_sent_at = :now, email_attempts = email_attempts + 1,
                email_next_attempt_at = NULL, email_last_error = NULL
            WHERE id = :id
            """, nativeQuery = true)
    int markEmailSent(@Param("id") UUID id, @Param("now") Instant now);

    /** Gửi lỗi: tăng số lần thử, hẹn lần sau; nextAttemptAt null + status FAILED khi hết lượt. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            UPDATE notifications
            SET email_status = :status, email_attempts = :attempts,
                email_next_attempt_at = :nextAttemptAt, email_last_error = :error
            WHERE id = :id
            """, nativeQuery = true)
    int markEmailFailedAttempt(@Param("id") UUID id, @Param("status") String status, @Param("attempts") int attempts,
                               @Param("nextAttemptAt") Instant nextAttemptAt, @Param("error") String error);
}
