package ra.edu.notificationservice.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ra.edu.notificationservice.entity.Notification;
import ra.edu.notificationservice.repository.NotificationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gửi email đã lưu trong bảng notifications (outbox cho email).
 *
 * - Ngay sau khi notification commit: gửi luôn (dispatch).
 * - SMTP lỗi: không mất thư, hẹn gửi lại sau 1, 5, 15, 30 phút; lần thứ 5 vẫn lỗi -> FAILED để đối soát.
 * - Job định kỳ quét thư đến hạn; claimEmail (UPDATE có điều kiện) bảo đảm một thư chỉ một nơi gửi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatcher {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(30)};

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final Clock clock = Clock.systemUTC();

    public void dispatch(UUID notificationId) {
        Instant now = clock.instant();
        if (notificationRepository.claimEmail(notificationId, now, now.plus(LEASE)) == 0) {
            return;   // đã gửi, đang có nơi khác gửi, hoặc chưa đến hạn
        }
        Notification n = notificationRepository.findById(notificationId).orElse(null);
        if (n == null) {
            return;
        }
        try {
            emailSender.send(n.getEmailTo(), n.getEmailSubject(), n.getEmailText(), n.getEmailHtml());
            notificationRepository.markEmailSent(notificationId, clock.instant());
        } catch (RuntimeException e) {
            int attempts = n.getEmailAttempts() + 1;
            String error = truncate(e.getMessage());
            if (attempts >= MAX_ATTEMPTS) {
                notificationRepository.markEmailFailedAttempt(notificationId, "FAILED", attempts, null, error);
                log.error("[EMAIL THẤT BẠI] bỏ cuộc sau {} lần: id={} to={} lỗi={}", attempts, notificationId, n.getEmailTo(), error);
            } else {
                Instant next = clock.instant().plus(BACKOFF[Math.min(attempts - 1, BACKOFF.length - 1)]);
                notificationRepository.markEmailFailedAttempt(notificationId, "PENDING", attempts, next, error);
                log.warn("[EMAIL LỖI] lần {}/{}: id={} to={} gửi lại lúc {} ({})",
                        attempts, MAX_ATTEMPTS, notificationId, n.getEmailTo(), next, error);
            }
        }
    }

    /** Gửi lại thư đến hạn (lỗi SMTP trước đó, hoặc service chết trước khi kịp gửi). */
    @Scheduled(fixedDelayString = "${app.mail.retry-interval-ms:30000}", initialDelayString = "${app.mail.retry-initial-delay-ms:20000}")
    public void retryDue() {
        List<UUID> due = notificationRepository.findDueEmailIds(clock.instant(), 20);
        due.forEach(this::dispatch);
    }

    private static String truncate(String s) {
        if (s == null) return "unknown error";
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
