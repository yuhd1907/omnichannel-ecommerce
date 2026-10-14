package ra.edu.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Message đã xử lý (idempotent consumer). Chỉ ghi qua
 * ProcessedMessageRepository.insertIfAbsent — không dùng save(): với ID tự gán,
 * save() là merge (SELECT rồi UPDATE) nên không phát hiện được trùng.
 */
@Entity
@Table(name = "processed_messages")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMessage {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
