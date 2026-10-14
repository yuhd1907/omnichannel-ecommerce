package ra.edu.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ra.edu.orderservice.client.dto.AddressInfo;
import ra.edu.orderservice.client.dto.ClientApiResponse;

import java.util.UUID;

/**
 * Feign client gọi internal endpoint của identity-service.
 * name = "identity-client" khớp với key trong feign.client.config.
 */
@FeignClient(name = "identity-client", url = "${app-clients.identity-service-url}")
public interface IdentityClient {

    /**
     * Lấy địa chỉ theo addressId kèm userId để kiểm tra ownership.
     * Order-service phải verify response.data().userId().equals(currentUserId)
     * trước khi dùng địa chỉ này.
     */
    @GetMapping("/internal/addresses/{addressId}")
    ClientApiResponse<AddressInfo> getAddress(@PathVariable("addressId") UUID addressId);
}
