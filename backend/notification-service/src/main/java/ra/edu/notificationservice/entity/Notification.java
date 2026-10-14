package ra.edu.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    // ---- Email (outbox): noi dung dung san, trang thai gui, lich gui lai ----
    @Column(name = "email_to")
    private String emailTo;

    @Column(name = "email_subject")
    private String emailSubject;

    @Column(name = "email_text", columnDefinition = "text")
    private String emailText;

    @Column(name = "email_html", columnDefinition = "text")
    private String emailHtml;

    /** PENDING | SENT | FAILED | SKIPPED */
    @Column(name = "email_status", nullable = false, length = 20)
    @Builder.Default
    private String emailStatus = "SKIPPED";

    @Column(name = "email_attempts", nullable = false)
    @Builder.Default
    private int emailAttempts = 0;

    @Column(name = "email_next_attempt_at")
    private Instant emailNextAttemptAt;

    @Column(name = "email_last_error", columnDefinition = "text")
    private String emailLastError;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
