package ra.edu.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.notificationservice.entity.Notification;
import ra.edu.notificationservice.entity.NotificationType;
import ra.edu.notificationservice.repository.NotificationRepository;
import ra.edu.notificationservice.repository.ProcessedMessageRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ProcessedMessageRepository processedMessageRepository;
    private final NotificationRepository     notificationRepository;

    /**
     * Ghi processed_messages + notifications trong MỘT transaction: không thể có trạng thái
     * "đã đánh dấu xử lý mà chưa gửi" hay "đã gửi mà chưa đánh dấu".
     *
     * @return false nếu message đã được xử lý trước đó (bỏ qua, vẫn ack).
     */
    @Transactional
    public boolean handleOrderEvent(String messageId, NotificationType type, UUID orderId, UUID userId) {
        if (processedMessageRepository.insertIfAbsent(messageId) == 0) {
            log.info("Duplicate message, skip (messageId={}, type={}, orderId={})", messageId, type, orderId);
            return false;
        }

        String content = switch (type) {
            case ORDER_CREATED   -> "Gửi xác nhận đơn " + orderId + " tới user " + userId;
            case ORDER_CANCELLED -> "Gửi thông báo hủy đơn " + orderId + " tới user " + userId;
        };
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .orderId(orderId)
                .type(type)
                .content(content)
                .build());

        log.info("[EMAIL GIẢ LẬP] {}", content);
        return true;
    }
}
