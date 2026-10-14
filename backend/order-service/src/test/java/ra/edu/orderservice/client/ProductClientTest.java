package ra.edu.orderservice.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ra.edu.grpc.product.BatchGetSkusRequest;
import ra.edu.grpc.product.BatchGetSkusResponse;
import ra.edu.grpc.product.GetSkuRequest;
import ra.edu.grpc.product.ProductInternalServiceGrpc;
import ra.edu.orderservice.client.dto.SkuInfo;
import ra.edu.orderservice.exception.BusinessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductClientTest {

    @Mock
    private ProductInternalServiceGrpc.ProductInternalServiceBlockingStub blockingStub;

    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        when(blockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(blockingStub);
        productClient = new ProductClient(blockingStub);
    }

    @Test
    @DisplayName("getSkuInfo: thành công -> parse đúng SkuInfo record kèm BigDecimal")
    void getSkuInfo_success() {
        ra.edu.grpc.product.SkuInfo proto = ra.edu.grpc.product.SkuInfo.newBuilder()
                .setSkuCode("AO-A-DO-M")
                .setProductName("Áo thun đỏ")
                .setPrice("150000.00")
                .setStatus("ACTIVE")
                .build();
        when(blockingStub.getSku(any(GetSkuRequest.class))).thenReturn(proto);

        SkuInfo result = productClient.getSkuInfo("AO-A-DO-M");

        assertThat(result.skuCode()).isEqualTo("AO-A-DO-M");
        assertThat(result.productName()).isEqualTo("Áo thun đỏ");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getSkuInfo: gRPC trả NOT_FOUND -> map thành BusinessException SKU_UNAVAILABLE (400)")
    void getSkuInfo_notFound() {
        when(blockingStub.getSku(any(GetSkuRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND.withDescription("SKU not found")));

        assertThatThrownBy(() -> productClient.getSkuInfo("NOT-EXIST"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("SKU_UNAVAILABLE");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("getSkuInfo: gRPC trả UNAVAILABLE -> map thành 503 PRODUCT_SERVICE_UNAVAILABLE")
    void getSkuInfo_serviceUnavailable() {
        when(blockingStub.getSku(any(GetSkuRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Connection refused")));

        assertThatThrownBy(() -> productClient.getSkuInfo("AO-A-DO-M"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("PRODUCT_SERVICE_UNAVAILABLE");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    @DisplayName("getSkuInfo: gRPC timeout DEADLINE_EXCEEDED -> map thành 503 PRODUCT_SERVICE_UNAVAILABLE")
    void getSkuInfo_deadlineExceeded() {
        when(blockingStub.getSku(any(GetSkuRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED.withDescription("Timeout")));

        assertThatThrownBy(() -> productClient.getSkuInfo("AO-A-DO-M"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("PRODUCT_SERVICE_UNAVAILABLE");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    @DisplayName("getSkuInfos: batch response chứa not_found -> ném BusinessException SKU_UNAVAILABLE")
    void getSkuInfos_containsNotFound() {
        BatchGetSkusResponse response = BatchGetSkusResponse.newBuilder()
                .addNotFound("KHONG-TON-TAI")
                .build();
        when(blockingStub.batchGetSkus(any(BatchGetSkusRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> productClient.getSkuInfos(List.of("KHONG-TON-TAI")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("SKU_UNAVAILABLE");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("getSkuInfos: batch lấy thành công -> trả về danh sách SkuInfo")
    void getSkuInfos_success() {
        ra.edu.grpc.product.SkuInfo proto = ra.edu.grpc.product.SkuInfo.newBuilder()
                .setSkuCode("SKU-1")
                .setProductName("Sản phẩm 1")
                .setPrice("200000.00")
                .setStatus("ACTIVE")
                .build();
        BatchGetSkusResponse response = BatchGetSkusResponse.newBuilder()
                .addSkus(proto)
                .build();
        when(blockingStub.batchGetSkus(any(BatchGetSkusRequest.class))).thenReturn(response);

        List<SkuInfo> list = productClient.getSkuInfos(List.of("SKU-1"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).skuCode()).isEqualTo("SKU-1");
        assertThat(list.get(0).price()).isEqualByComparingTo(new BigDecimal("200000.00"));
    }
}
