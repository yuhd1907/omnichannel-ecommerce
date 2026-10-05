package ra.edu.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ra.edu.orderservice.client.dto.ClientApiResponse;
import ra.edu.orderservice.client.dto.SkuInfo;

/**
 * Feign client gọi internal endpoint của product-service.
 * name = "product-client" khớp với key trong feign.client.config.
 * url lấy từ feign.client.config.product-client.url (application.yml).
 */
@FeignClient(name = "product-client", url = "${app-clients.product-service-url}")
public interface ProductClient {

    /**
     * Lấy thông tin SKU: tên sản phẩm, giá, trạng thái.
     * Ném FeignException nếu product-service không phản hồi hoặc trả 4xx/5xx.
     */
    @GetMapping("/internal/skus/{skuCode}")
    ClientApiResponse<SkuInfo> getSkuInfo(@PathVariable("skuCode") String skuCode);
}
