package ra.edu.orderservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ra.edu.orderservice.client.IdentityClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class FeignWarmupTest {

    @Test
    @DisplayName("Khởi động: gọi identity /actuator/health đúng 1 lần để Feign khởi tạo converter trên 1 thread")
    void run_callsHealthOnce() {
        IdentityClient client = mock(IdentityClient.class);
        when(client.health()).thenReturn(Map.of("status", "UP"));

        new FeignWarmup(client).run(null);

        verify(client, times(1)).health();
    }

    @Test
    @DisplayName("Identity chưa sẵn sàng -> chỉ ghi log, không làm hỏng việc khởi động order-service")
    void run_identityDown_doesNotThrow() {
        IdentityClient client = mock(IdentityClient.class);
        when(client.health()).thenThrow(new RuntimeException("Connection refused"));

        assertThatCode(() -> new FeignWarmup(client).run(null)).doesNotThrowAnyException();
    }
}
