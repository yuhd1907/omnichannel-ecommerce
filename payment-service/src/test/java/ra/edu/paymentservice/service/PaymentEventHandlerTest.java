package ra.edu.paymentservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ra.edu.paymentservice.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID  = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("150000.00");

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentEventHandler paymentEventHandler;

    @Test
    @DisplayName("handleOrderCreated -> gọi insertPendingIfAbsent thành công")
    void handleOrderCreated_success() {
        when(paymentRepository.insertPendingIfAbsent(ORDER_ID, USER_ID, AMOUNT)).thenReturn(1);

        paymentEventHandler.handleOrderCreated(ORDER_ID, USER_ID, AMOUNT);

        verify(paymentRepository, times(1)).insertPendingIfAbsent(ORDER_ID, USER_ID, AMOUNT);
    }

    @Test
    @DisplayName("handleOrderCreated nhận message lặp -> insert trả về 0 (idempotent)")
    void handleOrderCreated_duplicate() {
        when(paymentRepository.insertPendingIfAbsent(ORDER_ID, USER_ID, AMOUNT)).thenReturn(0);

        paymentEventHandler.handleOrderCreated(ORDER_ID, USER_ID, AMOUNT);

        verify(paymentRepository, times(1)).insertPendingIfAbsent(ORDER_ID, USER_ID, AMOUNT);
    }

    @Test
    @DisplayName("handleOrderCancelled -> gọi failIfPending thành công")
    void handleOrderCancelled_success() {
        when(paymentRepository.failIfPending(ORDER_ID)).thenReturn(1);

        paymentEventHandler.handleOrderCancelled(ORDER_ID);

        verify(paymentRepository, times(1)).failIfPending(ORDER_ID);
    }

    @Test
    @DisplayName("handleOrderCancelled -> đơn không ở PENDING hoặc đã xử lý -> failIfPending trả về 0 (idempotent)")
    void handleOrderCancelled_notPending() {
        when(paymentRepository.failIfPending(ORDER_ID)).thenReturn(0);

        paymentEventHandler.handleOrderCancelled(ORDER_ID);

        verify(paymentRepository, times(1)).failIfPending(ORDER_ID);
    }
}
