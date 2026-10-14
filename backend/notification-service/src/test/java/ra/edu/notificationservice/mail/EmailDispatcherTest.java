package ra.edu.notificationservice.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ra.edu.notificationservice.entity.Notification;
import ra.edu.notificationservice.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private EmailDispatcher dispatcher;

    private final UUID id = UUID.randomUUID();

    private Notification pending(int attempts) {
        return Notification.builder().id(id).emailTo("buyer@example.com").emailSubject("s").emailText("t")
                .emailHtml("<p>t</p>").emailStatus("PENDING").emailAttempts(attempts).build();
    }

    @Test
    @DisplayName("Giữ chỗ được + gửi thành công -> SENT")
    void send_success_marksSent() {
        when(notificationRepository.claimEmail(eq(id), any(), any())).thenReturn(1);
        when(notificationRepository.findById(id)).thenReturn(Optional.of(pending(0)));

        dispatcher.dispatch(id);

        verify(emailSender).send("buyer@example.com", "s", "t", "<p>t</p>");
        verify(notificationRepository).markEmailSent(eq(id), any());
        verify(notificationRepository, never()).markEmailFailedAttempt(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("SMTP lỗi lần 1 -> vẫn PENDING, attempts=1, hẹn gửi lại (không mất thư)")
    void send_failure_reschedules() {
        when(notificationRepository.claimEmail(eq(id), any(), any())).thenReturn(1);
        when(notificationRepository.findById(id)).thenReturn(Optional.of(pending(0)));
        doThrow(new EmailSender.MailDeliveryException("Connection refused"))
                .when(emailSender).send(any(), any(), any(), any());

        dispatcher.dispatch(id);

        verify(notificationRepository).markEmailFailedAttempt(eq(id), eq("PENDING"), eq(1), notNull(), contains("Connection refused"));
        verify(notificationRepository, never()).markEmailSent(any(), any());
    }

    @Test
    @DisplayName("Lỗi ở lần thử cuối -> FAILED, không hẹn nữa")
    void send_failure_lastAttempt_marksFailed() {
        when(notificationRepository.claimEmail(eq(id), any(), any())).thenReturn(1);
        when(notificationRepository.findById(id)).thenReturn(Optional.of(pending(EmailDispatcher.MAX_ATTEMPTS - 1)));
        doThrow(new EmailSender.MailDeliveryException("535 auth failed")).when(emailSender).send(any(), any(), any(), any());

        dispatcher.dispatch(id);

        verify(notificationRepository).markEmailFailedAttempt(eq(id), eq("FAILED"), eq(EmailDispatcher.MAX_ATTEMPTS), isNull(), any());
    }

    @Test
    @DisplayName("Không giữ chỗ được (đã gửi / nơi khác đang gửi) -> không gửi trùng")
    void notClaimed_doesNothing() {
        when(notificationRepository.claimEmail(eq(id), any(), any())).thenReturn(0);

        dispatcher.dispatch(id);

        verifyNoInteractions(emailSender);
        verify(notificationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Job gửi lại xử lý từng thư đến hạn")
    void retryDue_dispatchesEachDue() {
        when(notificationRepository.findDueEmailIds(any(Instant.class), eq(20))).thenReturn(List.of(id));
        when(notificationRepository.claimEmail(eq(id), any(), any())).thenReturn(0);

        dispatcher.retryDue();

        verify(notificationRepository).claimEmail(eq(id), any(), any());
    }
}
