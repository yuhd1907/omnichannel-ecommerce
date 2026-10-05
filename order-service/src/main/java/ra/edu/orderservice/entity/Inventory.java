package ra.edu.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku_code", nullable = false, unique = true, length = 100)
    private String skuCode;

    @Column(name = "available_qty", nullable = false)
    @Builder.Default
    private Long availableQty = 0L;

    @Column(name = "reserved_qty", nullable = false)
    @Builder.Default
    private Long reservedQty = 0L;

    /**
     * Optimistic locking version — tăng thủ công trong câu UPDATE có điều kiện,
     * KHÔNG dùng @Version để tránh xung đột với mệnh đề WHERE available_qty >= :q.
     */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
