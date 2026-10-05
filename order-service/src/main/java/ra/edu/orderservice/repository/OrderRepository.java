package ra.edu.orderservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ra.edu.orderservice.entity.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Danh sách đơn của một user, dùng index idx_orders_user_created_at. */
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    /** Lấy đơn kèm items trong 1 query (tránh N+1). */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);
}
