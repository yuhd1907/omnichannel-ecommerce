package ra.edu.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tên aggregate, ví dụ: "Order", "Payment". */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /** ID của aggregate tạo ra event. */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** Tên event, ví dụ: "ORDER_CREATED", "ORDER_CANCELLED". */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** Nội dung event dạng JSON — lưu jsonb trong Postgres. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    /** PENDING | SENT | FAILED */
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
