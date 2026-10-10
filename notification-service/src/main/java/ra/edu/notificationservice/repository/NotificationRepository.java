package ra.edu.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ra.edu.notificationservice.entity.Notification;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
