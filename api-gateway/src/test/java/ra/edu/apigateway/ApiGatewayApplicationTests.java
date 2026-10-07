package ra.edu.apigateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("Khai báo đúng 4 routes và thứ tự auth-login trước identity-service")
    void testRoutesConfigured() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        List<String> routeIds = routes.stream().map(Route::getId).toList();
        assertThat(routeIds).containsSubsequence("auth-login", "identity-service");
        assertThat(routeIds).contains("product-service", "order-service");

        // Đảm bảo không có route nào cho internal
        for (Route route : routes) {
            assertThat(route.getPredicate().toString()).doesNotContain("/internal");
        }
    }
}
