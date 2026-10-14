package ra.edu.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import ra.edu.grpc.product.ProductInternalServiceGrpc;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling   // OutboxRelay

@ImportGrpcClients(target = "product", types = ProductInternalServiceGrpc.ProductInternalServiceBlockingStub.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
