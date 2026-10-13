package ra.edu.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ra.edu.notificationservice.entity.Notification;
import ra.edu.notificationservice.entity.NotificationType;
import ra.edu.notificationservice.mail.EmailSender;
import ra.edu.notificationservice.repository.NotificationRepository;
import ra.edu.notificationservice.repository.ProcessedMessageRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ProcessedMessageRepository processedMessageRepository;
    private final NotificationRepository     notificationRepository;
    private final EmailSender                emailSender;

    /** Giữ cho code/test cũ: event không có email -> chỉ lưu notification, không gửi mail. */
    @Transactional
    public boolean handleOrderEvent(String messageId, NotificationType type, UUID orderId, UUID userId) {
        return handleOrderEvent(messageId, type, orderId, userId, null);
    }

    /**
     * Ghi processed_messages + notifications trong MỘT transaction: không thể có trạng thái
     * "đã đánh dấu xử lý mà chưa lưu" hay "đã lưu mà chưa đánh dấu".
     *
     * Email gửi SAU KHI commit: nếu commit lỗi thì message được retry và không có mail "ma";
     * nếu SMTP lỗi thì chỉ ghi log (xem EmailSender), dữ liệu không bị ảnh hưởng.
     *
     * @return false nếu message đã được xử lý trước đó (bỏ qua, vẫn ack).
     */
    @Transactional
    public boolean handleOrderEvent(String messageId, NotificationType type, UUID orderId, UUID userId,
                                    String customerEmail) {
        if (processedMessageRepository.insertIfAbsent(messageId) == 0) {
            log.info("Duplicate message, skip (messageId={}, type={}, orderId={})", messageId, type, orderId);
            return false;
        }

        String content = switch (type) {
            case ORDER_CREATED   -> "Gửi xác nhận đơn " + orderId + " tới user " + userId;
            case ORDER_CANCELLED -> "Gửi thông báo hủy đơn " + orderId + " tới user " + userId;
            case ORDER_CONFIRMED -> "Gửi xác nhận thanh toán đơn " + orderId + " tới user " + userId;
            case ORDER_SHIPPED   -> "Gửi thông báo đơn " + orderId + " đang được giao tới user " + userId;
            case ORDER_COMPLETED -> "Gửi thông báo đơn " + orderId + " đã giao thành công tới user " + userId;
        };
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .orderId(orderId)
                .type(type)
                .content(content)
                .build());

        String subject = subjectOf(type, orderId);
        String body = bodyOf(type, orderId);
        runAfterCommit(() -> emailSender.send(customerEmail, subject, body));
        return true;
    }

    private static String subjectOf(NotificationType type, UUID orderId) {
        String shortId = orderId.toString().substring(0, 8).toUpperCase();
        return switch (type) {
            case ORDER_CREATED   -> "[Omnichannel] Đã nhận đơn hàng #" + shortId;
            case ORDER_CANCELLED -> "[Omnichannel] Đơn hàng #" + shortId + " đã bị hủy";
            case ORDER_CONFIRMED -> "[Omnichannel] Thanh toán thành công đơn #" + shortId;
            case ORDER_SHIPPED   -> "[Omnichannel] Đơn hàng #" + shortId + " đang được giao";
            case ORDER_COMPLETED -> "[Omnichannel] Đơn hàng #" + shortId + " đã giao thành công";
        };
    }

    private static String bodyOf(NotificationType type, UUID orderId) {
        String detail = switch (type) {
            case ORDER_CREATED   -> "Chúng tôi đã nhận đơn hàng của bạn. Vui lòng thanh toán trong 15 phút để giữ hàng.";
            case ORDER_CANCELLED -> "Đơn hàng đã bị hủy (bạn hủy, thanh toán thất bại hoặc quá hạn thanh toán). Hàng đã được trả lại kho.";
            case ORDER_CONFIRMED -> "Chúng tôi đã nhận được thanh toán. Đơn hàng sẽ sớm được giao cho đơn vị vận chuyển.";
            case ORDER_SHIPPED   -> "Đơn hàng đã được giao cho đơn vị vận chuyển và đang trên đường tới bạn.";
            case ORDER_COMPLETED -> "Đơn hàng đã được giao thành công. Cảm ơn bạn đã mua sắm!";
        };
        return "Xin chào,\n\n" + detail + "\n\nMã đơn hàng: " + orderId + "\n\nOmnichannel E-Commerce";
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            // Gọi ngoài transaction (unit test) -> chạy luôn
            action.run();
        }
    }
}
