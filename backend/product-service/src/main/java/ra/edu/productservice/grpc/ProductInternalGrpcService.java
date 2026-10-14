package ra.edu.productservice.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import ra.edu.grpc.product.BatchGetSkusRequest;
import ra.edu.grpc.product.BatchGetSkusResponse;
import ra.edu.grpc.product.GetSkuRequest;
import ra.edu.grpc.product.ProductInternalServiceGrpc;
import ra.edu.grpc.product.SkuInfo;
import ra.edu.productservice.dto.SkuInfoDto;
import ra.edu.productservice.exception.ResourceNotFoundException;
import ra.edu.productservice.service.ProductService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ProductInternalGrpcService extends ProductInternalServiceGrpc.ProductInternalServiceImplBase {

    private final ProductService productService;

    @Override
    public void getSku(GetSkuRequest request, StreamObserver<SkuInfo> responseObserver) {
        String skuCode = request.getSkuCode();
        try {
            SkuInfoDto dto = productService.getSkuInfo(skuCode);
            SkuInfo reply = SkuInfo.newBuilder()
                    .setSkuCode(dto.skuCode())
                    .setProductName(dto.productName())
                    .setPrice(dto.price().toPlainString())
                    .setStatus(dto.status())
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (ResourceNotFoundException e) {
            log.warn("gRPC GetSku - SKU not found: {}", skuCode);
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC GetSku - Internal error for sku {}: {}", skuCode, e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
        }
    }

    @Override
    public void batchGetSkus(BatchGetSkusRequest request, StreamObserver<BatchGetSkusResponse> responseObserver) {
        List<String> skuCodes = request.getSkuCodesList();
        try {
            // Dùng 1 truy vấn duy nhất qua ProductService để tránh N+1 DB
            List<SkuInfoDto> foundSkus = productService.getSkuInfos(skuCodes);

            Set<String> foundCodes = new HashSet<>();
            BatchGetSkusResponse.Builder responseBuilder = BatchGetSkusResponse.newBuilder();

            for (SkuInfoDto dto : foundSkus) {
                foundCodes.add(dto.skuCode());
                responseBuilder.addSkus(SkuInfo.newBuilder()
                        .setSkuCode(dto.skuCode())
                        .setProductName(dto.productName())
                        .setPrice(dto.price().toPlainString())
                        .setStatus(dto.status())
                        .build());
            }

            // Các SKU không tìm thấy được trả về qua danh sách not_found (không throw lỗi cho cả batch)
            for (String code : skuCodes) {
                if (!foundCodes.contains(code)) {
                    responseBuilder.addNotFound(code);
                }
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC BatchGetSkus - Internal error: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
        }
    }
}
