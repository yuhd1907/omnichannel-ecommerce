package ra.edu.productservice.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ra.edu.grpc.product.BatchGetSkusRequest;
import ra.edu.grpc.product.BatchGetSkusResponse;
import ra.edu.grpc.product.GetSkuRequest;
import ra.edu.grpc.product.SkuInfo;
import ra.edu.productservice.dto.SkuInfoDto;
import ra.edu.productservice.exception.ResourceNotFoundException;
import ra.edu.productservice.service.ProductService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductInternalGrpcServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private StreamObserver<SkuInfo> skuInfoObserver;

    @Mock
    private StreamObserver<BatchGetSkusResponse> batchResponseObserver;

    private ProductInternalGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new ProductInternalGrpcService(productService);
    }

    @Test
    @DisplayName("GetSku: SKU tồn tại -> trả về SkuInfo với price dạng string và status ACTIVE")
    void getSku_success() {
        SkuInfoDto dto = new SkuInfoDto("SKU-1", "Áo thun", new BigDecimal("120000.00"), "ACTIVE");
        when(productService.getSkuInfo("SKU-1")).thenReturn(dto);

        grpcService.getSku(GetSkuRequest.newBuilder().setSkuCode("SKU-1").build(), skuInfoObserver);

        ArgumentCaptor<SkuInfo> captor = ArgumentCaptor.forClass(SkuInfo.class);
        verify(skuInfoObserver).onNext(captor.capture());
        verify(skuInfoObserver).onCompleted();

        SkuInfo captured = captor.getValue();
        assertThat(captured.getSkuCode()).isEqualTo("SKU-1");
        assertThat(captured.getProductName()).isEqualTo("Áo thun");
        assertThat(captured.getPrice()).isEqualTo("120000.00");
        assertThat(captured.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("GetSku: SKU không tồn tại -> trả về gRPC Status NOT_FOUND")
    void getSku_notFound() {
        when(productService.getSkuInfo("NOT-EXIST")).thenThrow(new ResourceNotFoundException("SKU_NOT_FOUND", "SKU not found"));

        grpcService.getSku(GetSkuRequest.newBuilder().setSkuCode("NOT-EXIST").build(), skuInfoObserver);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(skuInfoObserver).onError(errorCaptor.capture());
        verify(skuInfoObserver, never()).onCompleted();

        assertThat(errorCaptor.getValue()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) errorCaptor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    @DisplayName("BatchGetSkus: trả về danh sách SKU kèm danh sách not_found cho SKU không tồn tại")
    void batchGetSkus_mixed() {
        SkuInfoDto foundSku = new SkuInfoDto("SKU-1", "Áo thun", new BigDecimal("99000.00"), "ACTIVE");
        when(productService.getSkuInfos(List.of("SKU-1", "SKU-2"))).thenReturn(List.of(foundSku));

        BatchGetSkusRequest request = BatchGetSkusRequest.newBuilder()
                .addAllSkuCodes(List.of("SKU-1", "SKU-2"))
                .build();

        grpcService.batchGetSkus(request, batchResponseObserver);

        ArgumentCaptor<BatchGetSkusResponse> captor = ArgumentCaptor.forClass(BatchGetSkusResponse.class);
        verify(batchResponseObserver).onNext(captor.capture());
        verify(batchResponseObserver).onCompleted();

        BatchGetSkusResponse response = captor.getValue();
        assertThat(response.getSkusCount()).isEqualTo(1);
        assertThat(response.getSkus(0).getSkuCode()).isEqualTo("SKU-1");
        assertThat(response.getSkus(0).getPrice()).isEqualTo("99000.00");

        assertThat(response.getNotFoundList()).containsExactly("SKU-2");
    }
}
