package ra.edu.orderservice.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ra.edu.grpc.product.BatchGetSkusRequest;
import ra.edu.grpc.product.BatchGetSkusResponse;
import ra.edu.grpc.product.GetSkuRequest;
import ra.edu.grpc.product.ProductInternalServiceGrpc;
import ra.edu.orderservice.client.dto.SkuInfo;
import ra.edu.orderservice.exception.BusinessException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client gọi ProductInternalService của product-service.
 * Đóng gói toàn bộ logic gọi stub, timeout deadline 2s, mapping exception sang BusinessException.
 * Giữ sạch abstraction cho OrderServiceImpl.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    private static final long DEADLINE_SECONDS = 2;

    private final ProductInternalServiceGrpc.ProductInternalServiceBlockingStub productBlockingStub;

    /**
     * Lấy thông tin 1 SKU qua gRPC GetSku RPC.
     */
    public SkuInfo getSkuInfo(String skuCode) {
        GetSkuRequest request = GetSkuRequest.newBuilder()
                .setSkuCode(skuCode)
                .build();
        try {
            ra.edu.grpc.product.SkuInfo response = productBlockingStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getSku(request);
            return toDto(response);
        } catch (StatusRuntimeException e) {
            log.error("gRPC call GetSku failed: skuCode={}, status={}, description={}",
                    skuCode, e.getStatus().getCode(), e.getStatus().getDescription(), e);
            throw mapStatusException(e, skuCode);
        } catch (Exception e) {
            log.error("Unexpected error in gRPC GetSku for skuCode={}", skuCode, e);
            throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE",
                    "Could not retrieve product info, please try again", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Batch lấy thông tin nhiều SKU qua gRPC BatchGetSkus RPC (giải quyết N+1 round-trip).
     */
    public List<SkuInfo> getSkuInfos(List<String> skuCodes) {
        if (skuCodes == null || skuCodes.isEmpty()) {
            return Collections.emptyList();
        }

        BatchGetSkusRequest request = BatchGetSkusRequest.newBuilder()
                .addAllSkuCodes(skuCodes)
                .build();

        try {
            BatchGetSkusResponse response = productBlockingStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .batchGetSkus(request);

            if (response.getNotFoundCount() > 0) {
                String missingSku = response.getNotFoundList().get(0);
                log.warn("gRPC BatchGetSkus returned missing skus: {}", response.getNotFoundList());
                throw new BusinessException("SKU_UNAVAILABLE",
                        "Product SKU not found: " + missingSku, HttpStatus.BAD_REQUEST);
            }

            return response.getSkusList().stream()
                    .map(this::toDto)
                    .toList();
        } catch (StatusRuntimeException e) {
            log.error("gRPC call BatchGetSkus failed: skuCodes={}, status={}, description={}",
                    skuCodes, e.getStatus().getCode(), e.getStatus().getDescription(), e);
            throw mapStatusException(e, skuCodes.toString());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in gRPC BatchGetSkus for skuCodes={}", skuCodes, e);
            throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE",
                    "Could not retrieve product info, please try again", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private BusinessException mapStatusException(StatusRuntimeException e, String target) {
        Status.Code code = e.getStatus().getCode();
        if (code == Status.Code.NOT_FOUND) {
            return new BusinessException("SKU_UNAVAILABLE",
                    "Product SKU not found: " + target, HttpStatus.BAD_REQUEST);
        }
        if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
            return new BusinessException("PRODUCT_SERVICE_UNAVAILABLE",
                    "Product service unavailable or timeout, please try again", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new BusinessException("PRODUCT_SERVICE_ERROR",
                "Product service error: " + e.getStatus().getDescription(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private SkuInfo toDto(ra.edu.grpc.product.SkuInfo proto) {
        return new SkuInfo(
                proto.getSkuCode(),
                proto.getProductName(),
                new BigDecimal(proto.getPrice()),
                proto.getStatus()
        );
    }
}
