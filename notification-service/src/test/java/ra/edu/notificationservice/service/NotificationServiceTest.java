package ra.edu.notificationservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ra.edu.notificationservice.entity.Notification;
import ra.edu.notificationservice.entity.NotificationType;
import ra.edu.notificationservice.repository.NotificationRepository;
import ra.edu.notificationservice.repository.ProcessedMessageRepository;

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

    @InjectMocks
    private NotificationService notificationService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId  = UUID.randomUUID();

    @Test
    @DisplayName("Message mới -> ghi processed + lưu notification")
    void newMessage_savesNotification() {
        when(processedMessageRepository.insertIfAbsent("msg-1")).thenReturn(1);

        boolean handled = notificationService.handleOrderEvent("msg-1", NotificationType.ORDER_CREATED, orderId, userId);

        assertThat(handled).isTrue();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.ORDER_CREATED);
        assertThat(saved.getValue().getContent()).contains(orderId.toString()).contains(userId.toString());
    }

    @Test
    @DisplayName("Message trùng (insertIfAbsent = 0) -> bỏ qua, KHÔNG lưu notification")
    void duplicateMessage_isSkipped() {
        when(processedMessageRepository.insertIfAbsent("msg-1")).thenReturn(0);

        boolean handled = notificationService.handleOrderEvent("msg-1", NotificationType.ORDER_CREATED, orderId, userId);

        assertThat(handled).isFalse();
        verify(notificationRepository, never()).save(any());
    }
}
