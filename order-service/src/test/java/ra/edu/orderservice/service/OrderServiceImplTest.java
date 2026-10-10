package ra.edu.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ra.edu.common.response.ErrorCode;
import ra.edu.orderservice.client.IdentityClient;
import ra.edu.orderservice.client.ProductClient;
import ra.edu.orderservice.client.dto.AddressInfo;
import ra.edu.orderservice.client.dto.ClientApiResponse;
import ra.edu.orderservice.client.dto.SkuInfo;
import ra.edu.orderservice.dto.OrderDto;
import ra.edu.orderservice.dto.request.CreateOrderRequest;
import ra.edu.orderservice.entity.Order;
import ra.edu.orderservice.entity.OutboxEvent;
import ra.edu.orderservice.exception.BusinessException;
import ra.edu.orderservice.repository.InventoryRepository;
import ra.edu.orderservice.repository.OrderRepository;
import ra.edu.orderservice.repository.OutboxEventRepository;
import ra.edu.orderservice.service.impl.OrderServiceImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private IdentityClient identityClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock mockLock;

    private OrderServiceImpl orderService;

    private final UUID userId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderRepository,
                inventoryRepository,
                outboxEventRepository,
                productClient,
                identityClient,
                transactionTemplate,
                redissonClient
        );

        // Giả lập transactionTemplate.execute thực thi callback trực tiếp
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    @DisplayName("createOrder: thành công -> lấy Redisson lock, thực hiện transaction và nhả lock trong finally")
    void createOrder_successWithLock() throws Exception {
        // Given
        AddressInfo addressInfo = new AddressInfo(
                addressId, userId, "Nguyen Van A", "0987654321", "123 Le Loi", "Ben Nghe", "Quan 1", "TP HCM", true
        );
        when(identityClient.getAddress(addressId)).thenReturn(new ClientApiResponse<>("SUCCESS", "OK", addressInfo));

        SkuInfo skuInfo = new SkuInfo("SKU-01", "Ao Polo", new BigDecimal("200000.00"), "ACTIVE");
        when(productClient.getSkuInfos(List.of("SKU-01"))).thenReturn(List.of(skuInfo));

        when(redissonClient.getLock("lock:inventory:SKU-01")).thenReturn(mockLock);
        when(mockLock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(inventoryRepository.reserve("SKU-01", 2L)).thenReturn(1);

        CreateOrderRequest request = new CreateOrderRequest(
                "WEB",
                addressId,
                List.of(new CreateOrderRequest.OrderItemRequest("SKU-01", 2L))
        );

        // When
        OrderDto result = orderService.createOrder(userId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("400000.00"));
        assertThat(result.status()).isEqualTo("PENDING");

        // Verify Lock & Transaction lifecycle
        verify(redissonClient).getLock("lock:inventory:SKU-01");
        verify(mockLock).tryLock(3L, TimeUnit.SECONDS);
        verify(transactionTemplate).execute(any());
        verify(mockLock).unlock();
    }

    @Test
    @DisplayName("createOrder: đơn nhiều SKU -> sắp xếp sort và dùng getMultiLock")
    void createOrder_multiSkuLock() throws Exception {
        AddressInfo addressInfo = new AddressInfo(
                addressId, userId, "Nguyen Van A", "0987654321", "123 Le Loi", "Ben Nghe", "Quan 1", "TP HCM", true
        );
        when(identityClient.getAddress(addressId)).thenReturn(new ClientApiResponse<>("SUCCESS", "OK", addressInfo));

        SkuInfo sku1 = new SkuInfo("SKU-A", "San pham A", new BigDecimal("100000.00"), "ACTIVE");
        SkuInfo sku2 = new SkuInfo("SKU-B", "San pham B", new BigDecimal("200000.00"), "ACTIVE");
        when(productClient.getSkuInfos(List.of("SKU-A", "SKU-B"))).thenReturn(List.of(sku1, sku2));

        RLock lockA = mock(RLock.class);
        RLock lockB = mock(RLock.class);
        RLock multiLock = mock(RLock.class);

        when(redissonClient.getLock("lock:inventory:SKU-A")).thenReturn(lockA);
        when(redissonClient.getLock("lock:inventory:SKU-B")).thenReturn(lockB);
        when(redissonClient.getMultiLock(lockA, lockB)).thenReturn(multiLock);
        when(multiLock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(true);
        when(multiLock.isHeldByCurrentThread()).thenReturn(true);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.reserve(anyString(), anyLong())).thenReturn(1);

        CreateOrderRequest request = new CreateOrderRequest(
                "WEB",
                addressId,
                List.of(
                        new CreateOrderRequest.OrderItemRequest("SKU-B", 1L), // Gửi B trước A để test sort
                        new CreateOrderRequest.OrderItemRequest("SKU-A", 1L)
                )
        );

        OrderDto result = orderService.createOrder(userId, request);

        assertThat(result).isNotNull();
        verify(redissonClient).getMultiLock(lockA, lockB);
        verify(multiLock).tryLock(3L, TimeUnit.SECONDS);
        verify(multiLock).unlock();
    }

    @Test
    @DisplayName("createOrder: không lấy được lock -> ném INVENTORY_BUSY (409)")
    void createOrder_inventoryBusyWhenLockFails() throws Exception {
        AddressInfo addressInfo = new AddressInfo(
                addressId, userId, "Nguyen Van A", "0987654321", "123 Le Loi", "Ben Nghe", "Quan 1", "TP HCM", true
        );
        when(identityClient.getAddress(addressId)).thenReturn(new ClientApiResponse<>("SUCCESS", "OK", addressInfo));

        SkuInfo skuInfo = new SkuInfo("SKU-01", "Ao Polo", new BigDecimal("200000.00"), "ACTIVE");
        when(productClient.getSkuInfos(List.of("SKU-01"))).thenReturn(List.of(skuInfo));

        when(redissonClient.getLock("lock:inventory:SKU-01")).thenReturn(mockLock);
        when(mockLock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(false); // Lock bận

        CreateOrderRequest request = new CreateOrderRequest(
                "WEB",
                addressId,
                List.of(new CreateOrderRequest.OrderItemRequest("SKU-01", 2L))
        );

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.INVENTORY_BUSY);
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        // Không bao giờ chạy vào transaction hoặc unlock khi tryLock fail
        verify(transactionTemplate, never()).execute(any());
        verify(mockLock, never()).unlock();
    }

    @Test
    @DisplayName("createOrder: hết hàng (rowCount == 0) -> ném OUT_OF_STOCK (409)")
    void createOrder_outOfStock() throws Exception {
        AddressInfo addressInfo = new AddressInfo(
                addressId, userId, "Nguyen Van A", "0987654321", "123 Le Loi", "Ben Nghe", "Quan 1", "TP HCM", true
        );
        when(identityClient.getAddress(addressId)).thenReturn(new ClientApiResponse<>("SUCCESS", "OK", addressInfo));

        SkuInfo skuInfo = new SkuInfo("SKU-01", "Ao Polo", new BigDecimal("200000.00"), "ACTIVE");
        when(productClient.getSkuInfos(List.of("SKU-01"))).thenReturn(List.of(skuInfo));

        when(redissonClient.getLock("lock:inventory:SKU-01")).thenReturn(mockLock);
        when(mockLock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.reserve("SKU-01", 2L)).thenReturn(0); // Không đủ hàng

        CreateOrderRequest request = new CreateOrderRequest(
                "WEB",
                addressId,
                List.of(new CreateOrderRequest.OrderItemRequest("SKU-01", 2L))
        );

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("OUT_OF_STOCK");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(outboxEventRepository, never()).save(any());
        verify(mockLock).unlock(); // Lock vẫn được nhả an toàn trong finally
    }
}
