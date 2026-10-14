package ra.edu.orderservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ra.edu.orderservice.client.IdentityClient;

/**
 * Làm nóng Feign client của identity-service ngay khi order-service khởi động.
 *
 * Lỗi đã gặp (docs/Flashsale_Results.md, "Phát hiện phụ"): Spring Cloud OpenFeign khởi tạo
 * HttpMessageConverters theo kiểu lười và không thread-safe. Nếu lời gọi Feign ĐẦU TIÊN là một
 * loạt request đồng thời, một phần thread thấy danh sách converter còn rỗng
 * ("'messageConverters' must not be empty") và đơn hàng nhận 503 IDENTITY_SERVICE_UNAVAILABLE.
 *
 * Gọi một lần, tuần tự, trước khi có traffic -> phần khởi tạo lười chạy xong trên một thread.
 * Lớp bảo vệ thứ hai: OrderServiceImpl thử lại 1 lần khi gặp lỗi tạm thời (khe hở nhỏ giữa lúc
 * Tomcat nhận request và lúc runner này chạy xong). Lỗi ở đây chỉ ghi log, không chặn khởi động.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignWarmup implements ApplicationRunner {

    private final IdentityClient identityClient;

    @Override
    public void run(ApplicationArguments args) {
        long start = System.nanoTime();
        try {
            identityClient.health();
            log.info("Feign warm-up: identity-service OK ({} ms)", (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            log.warn("Feign warm-up failed, first orders may retry once: {}", e.getMessage());
        }
    }
}
