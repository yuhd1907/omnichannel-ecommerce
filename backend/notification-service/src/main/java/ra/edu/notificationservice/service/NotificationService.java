package ra.edu.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ra.edu.notificationservice.entity.Notification;
import ra.edu.notificationservice.entity.NotificationType;
import ra.edu.notificationservice.mail.EmailDispatcher;
import ra.edu.notificationservice.mail.EmailSender;
import ra.edu.notificationservice.mail.EmailTemplates;
import ra.edu.notificationservice.repository.NotificationRepository;
import ra.edu.notificationservice.repository.ProcessedMessageRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ProcessedMessageRepository processedMessageRepository;
    private final NotificationRepository     notificationRepository;
    private final EmailSender                emailSender;
    private final EmailTemplates             emailTemplates;
    private final EmailDispatcher            emailDispatcher;

    /** Giữ cho code/test cũ: event không có email -> chỉ lưu notification, không gửi mail. */
    @Transactional
    public boolean handleOrderEvent(String messageId, NotificationType type, UUID orderId, UUID userId) {
        return handleOrderEvent(messageId, type, orderId, userId, null, null, null);
    }

    @Transactional
    public boolean handleOrderEvent(String messageId, NotificationType type, UUID orderId, UUID userId,
                                    String customerEmail) {
        return handleOrderEvent(messageId, type, orderId, userId, customerEmail, null, null);
    }

    /**
     * Ghi processed_messages + notification (kèm nội dung email, trạng thái PENDING) trong MỘT
     * transaction: event lặp không tạo thư trùng, và thư không bao giờ "mất" giữa chừng.
     *
     * Gửi SAU KHI commit (EmailDispatcher). SMTP lỗi thì thư vẫn nằm trong DB ở PENDING và được
     * job gửi lại theo backoff — thay cho cách cũ chỉ ghi log rồi bỏ.
     *
     * @param paymentMethod COD / VNPAY... (null nếu event không kèm) -> nội dung đúng nghiệp vụ COD
     * @param totalAmount   tổng tiền đơn (null nếu event không kèm) -> hiện trong email
     * @return false nếu message đã được xử lý trước đó (bỏ qua, vẫn ack).
     */
    @Transactional
    public boolean handleOrderEvent(String messageId, NotificationType type, UUID orderId, UUID userId,
                                    String customerEmail, String paymentMethod, BigDecimal totalAmount) {
        if (processedMessageRepository.insertIfAbsent(messageId) == 0) {
            log.info("Duplicate message, skip (messageId={}, type={}, orderId={})", messageId, type, orderId);
            return false;
        }

        boolean cod = "COD".equalsIgnoreCase(paymentMethod);
        String content = switch (type) {
            case ORDER_CREATED   -> "Gửi xác nhận đơn " + orderId + " tới user " + userId;
            case ORDER_CANCELLED -> "Gửi thông báo hủy đơn " + orderId + " tới user " + userId;
            case ORDER_CONFIRMED -> (cod ? "Gửi xác nhận đơn COD " : "Gửi xác nhận thanh toán đơn ") + orderId + " tới user " + userId;
            case ORDER_SHIPPED   -> "Gửi thông báo đơn " + orderId + " đang được giao tới user " + userId;
            case ORDER_COMPLETED -> "Gửi thông báo đơn " + orderId + " đã giao thành công tới user " + userId;
        };

        EmailTemplates.Email email = emailTemplates.build(type, orderId, paymentMethod, totalAmount);
        boolean willSend = customerEmail != null && !customerEmail.isBlank() && emailSender.isEnabled();
        if (!willSend) {
            log.info("[EMAIL BỎ QUA] {} (to={}, mail enabled={})", email.subject(), customerEmail, emailSender.isEnabled());
        }

        Notification saved = notificationRepository.save(Notification.builder()
                .userId(userId)
                .orderId(orderId)
                .type(type)
                .content(content)
                .emailTo(customerEmail)
                .emailSubject(email.subject())
                .emailText(email.text())
                .emailHtml(email.html())
                .emailStatus(willSend ? "PENDING" : "SKIPPED")
                .emailNextAttemptAt(willSend ? Instant.now() : null)
                .build());

        if (willSend && saved != null && saved.getId() != null) {
            UUID id = saved.getId();
            runAfterCommit(() -> emailDispatcher.dispatch(id));
        }
        return true;
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
