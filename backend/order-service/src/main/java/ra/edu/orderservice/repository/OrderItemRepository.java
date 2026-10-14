package ra.edu.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ra.edu.orderservice.entity.OrderItem;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);
}
