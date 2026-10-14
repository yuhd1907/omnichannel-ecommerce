package ra.edu.notificationservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ra.edu.notificationservice.entity.Notification;
import ra.edu.notificationservice.entity.NotificationType;
import ra.edu.notificationservice.mail.EmailDispatcher;
import ra.edu.notificationservice.mail.EmailSender;
import ra.edu.notificationservice.mail.EmailTemplates;
import ra.edu.notificationservice.repository.NotificationRepository;
import ra.edu.notificationservice.repository.ProcessedMessageRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailSender emailSender;

    @Spy
    private EmailTemplates emailTemplates = new EmailTemplates("http://localhost:3001");

    @Mock
    private EmailDispatcher emailDispatcher;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId  = UUID.randomUUID();

    @Test
    @DisplayName("Message mới, không có email -> lưu notification, email SKIPPED, không gửi")
    void newMessage_withoutEmail_savesSkipped() {
        when(processedMessageRepository.insertIfAbsent("msg-1")).thenReturn(1);

        boolean handled = notificationService.handleOrderEvent("msg-1", NotificationType.ORDER_CREATED, orderId, userId);

        assertThat(handled).isTrue();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.ORDER_CREATED);
        assertThat(saved.getValue().getContent()).contains(orderId.toString()).contains(userId.toString());
        assertThat(saved.getValue().getEmailStatus()).isEqualTo("SKIPPED");
        verifyNoInteractions(emailDispatcher);
    }

    @Test
    @DisplayName("Có email + bật gửi mail -> lưu PENDING kèm nội dung text/HTML rồi giao cho dispatcher")
    void newMessage_withEmail_savesPendingAndDispatches() {
        UUID notificationId = UUID.randomUUID();
        when(processedMessageRepository.insertIfAbsent("msg-2")).thenReturn(1);
        when(emailSender.isEnabled()).thenReturn(true);
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(notificationId);
            return n;
        });

        notificationService.handleOrderEvent("msg-2", NotificationType.ORDER_SHIPPED, orderId, userId, "buyer@example.com");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getEmailStatus()).isEqualTo("PENDING");
        assertThat(n.getEmailTo()).isEqualTo("buyer@example.com");
        assertThat(n.getEmailSubject()).contains("đang được giao");
        assertThat(n.getEmailText()).contains(orderId.toString());
        assertThat(n.getEmailHtml()).contains("Xem đơn hàng").contains("http://localhost:3001/orders/" + orderId);
        assertThat(n.getEmailNextAttemptAt()).isNotNull();
        verify(emailDispatcher).dispatch(notificationId);
    }

    @Test
    @DisplayName("Đơn COD được xác nhận -> email ghi 'thanh toán khi nhận hàng', không ghi 'thanh toán thành công'")
    void codConfirmed_usesCodWording() {
        when(processedMessageRepository.insertIfAbsent("msg-3")).thenReturn(1);
        when(emailSender.isEnabled()).thenReturn(true);

        notificationService.handleOrderEvent("msg-3", NotificationType.ORDER_CONFIRMED, orderId, userId,
                "buyer@example.com", "COD", new BigDecimal("29990000"));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailSubject()).contains("thanh toán khi nhận hàng").doesNotContain("Thanh toán thành công");
        assertThat(saved.getValue().getEmailHtml()).contains("29.990.000");
    }

    @Test
    @DisplayName("Tắt gửi mail (MAIL_ENABLED=false) -> SKIPPED dù có địa chỉ")
    void mailDisabled_savesSkipped() {
        when(processedMessageRepository.insertIfAbsent("msg-4")).thenReturn(1);
        when(emailSender.isEnabled()).thenReturn(false);

        notificationService.handleOrderEvent("msg-4", NotificationType.ORDER_CREATED, orderId, userId, "buyer@example.com");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailStatus()).isEqualTo("SKIPPED");
        verifyNoInteractions(emailDispatcher);
    }

    @Test
    @DisplayName("Message trùng (insertIfAbsent = 0) -> bỏ qua, KHÔNG lưu notification")
    void duplicateMessage_isSkipped() {
        when(processedMessageRepository.insertIfAbsent("msg-1")).thenReturn(0);

        boolean handled = notificationService.handleOrderEvent("msg-1", NotificationType.ORDER_CREATED, orderId, userId);

        assertThat(handled).isFalse();
        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(emailDispatcher);
    }
}
