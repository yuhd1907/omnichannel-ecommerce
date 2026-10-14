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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ra.edu.common.response.ErrorCode;
import ra.edu.orderservice.client.IdentityClient;
import ra.edu.orderservice.client.ProductClient;
import ra.edu.orderservice.client.dto.AddressInfo;
import ra.edu.orderservice.client.dto.ClientApiResponse;
import ra.edu.orderservice.client.dto.SkuInfo;
import ra.edu.orderservice.dto.CreateOrderResult;
import ra.edu.orderservice.dto.OrderDto;
import ra.edu.orderservice.dto.request.CreateOrderRequest;
import ra.edu.orderservice.entity.Inventory;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private OrderStateService orderStateService;

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
                redissonClient,
                orderStateService
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
        OrderDto result = orderService.createOrder(userId, "buyer@example.com", request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("400000.00"));
        assertThat(result.status()).isEqualTo("PENDING");

        // Verify Lock & Transaction lifecycle
        verify(redissonClient).getLock("lock:inventory:SKU-01");
        verify(mockLock).tryLock(3L, TimeUnit.SECONDS);
        verify(transactionTemplate).execute(any());
        verify(mockLock).unlock();

        // Email người đặt (từ JWT) nằm trong payload ORDER_CREATED để notification gửi mail
        org.mockito.ArgumentCaptor<OutboxEvent> outbox = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outbox.capture());
        assertThat(outbox.getValue().getPayload()).contains("\"customerEmail\":\"buyer@example.com\"");
    }

    @Test
    @DisplayName("createOrder: kho đã không đủ -> 409 OUT_OF_STOCK ngay, KHÔNG gọi identity/product/lock")
    void createOrder_fastRejectWhenSurelyOutOfStock() {
        when(inventoryRepository.findBySkuCodeIn(any())).thenReturn(List.of(
                Inventory.builder().skuCode("SKU-01").availableQty(1L).reservedQty(0L).build()));

        CreateOrderRequest request = new CreateOrderRequest(
                "WEB",
                addressId,
                List.of(new CreateOrderRequest.OrderItemRequest("SKU-01", 2L))
        );

        assertThatThrownBy(() -> orderService.createOrder(userId, "buyer@example.com", request))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verifyNoInteractions(identityClient, productClient, redissonClient);
        verify(transactionTemplate, never()).execute(any());
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

        OrderDto result = orderService.createOrder(userId, "buyer@example.com", request);

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

        assertThatThrownBy(() -> orderService.createOrder(userId, "buyer@example.com", request))
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

        assertThatThrownBy(() -> orderService.createOrder(userId, "buyer@example.com", request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("OUT_OF_STOCK");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(outboxEventRepository, never()).save(any());
        verify(mockLock).unlock(); // Lock vẫn được nhả an toàn trong finally
    }

    // ─────────────────────────── cancelOrder ──────────────────────────

    private Order pendingOrder(UUID orderId, UUID ownerId) {
        return Order.builder().id(orderId).userId(ownerId).channel("WEB").status("PENDING")
                .shippingRecipientName("A").shippingPhone("0900000000").shippingAddress("HCM").build();
    }

    // ── Idempotency-Key ──────────────────────────────────────────────────

    private CreateOrderRequest sku01x2() {
        return new CreateOrderRequest("WEB", addressId,
                List.of(new CreateOrderRequest.OrderItemRequest("SKU-01", 2L)));
    }

    private static OrderRepository.IdempotencyRecord record(String hash, UUID orderId) {
        return new OrderRepository.IdempotencyRecord() {
            @Override public String getRequestHash() { return hash; }
            @Override public UUID getOrderId()       { return orderId; }
        };
    }

    private Order existingOrder(UUID orderId) {
        Order o = Order.builder().userId(userId).channel("WEB")
                .shippingRecipientName("A").shippingPhone("0900000000").shippingAddress("x")
                .totalAmount(new BigDecimal("400000.00")).build();
        o.setId(orderId);
        o.setItems(new java.util.ArrayList<>());
        return o;
    }

    @Test
    @DisplayName("Idempotency: key đã dùng với cùng nội dung -> trả đơn cũ (replayed), không gọi mạng, không giữ kho")
    void createOrder_sameKeySameBody_replaysWithoutSideEffects() {
        UUID orderId = UUID.randomUUID();
        String hash = OrderServiceImpl.requestHash("WEB", addressId,
                new java.util.TreeMap<>(java.util.Map.of("SKU-01", 2L)));
        when(orderRepository.findIdempotencyRecord(userId, "key-1")).thenReturn(java.util.Optional.of(record(hash, orderId)));
        when(orderRepository.findWithItemsById(orderId)).thenReturn(java.util.Optional.of(existingOrder(orderId)));

        CreateOrderResult result = orderService.createOrder(userId, "buyer@example.com", sku01x2(), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(orderId);
        verifyNoInteractions(identityClient, productClient, redissonClient, inventoryRepository, transactionTemplate);
    }

    @Test
    @DisplayName("Idempotency: cùng key nhưng khác nội dung -> 422 IDEMPOTENCY_KEY_REUSED")
    void createOrder_sameKeyDifferentBody_422() {
        when(orderRepository.findIdempotencyRecord(userId, "key-1"))
                .thenReturn(java.util.Optional.of(record("hash-of-another-request", UUID.randomUUID())));

        assertThatThrownBy(() -> orderService.createOrder(userId, "buyer@example.com", sku01x2(), "key-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
        verifyNoInteractions(identityClient, productClient, transactionTemplate);
    }

    @Test
    @DisplayName("Idempotency: key mới -> tạo đơn và ghi key cùng transaction với đơn")
    void createOrder_newKey_storesKeyInSameTransaction() throws Exception {
        stubHappyPathForSku01();
        when(orderRepository.findIdempotencyRecord(userId, "key-1")).thenReturn(java.util.Optional.empty());
        when(orderRepository.insertIdempotencyKey(any(), any(), any(), any())).thenReturn(1);

        CreateOrderResult result = orderService.createOrder(userId, "buyer@example.com", sku01x2(), "key-1");

        assertThat(result.replayed()).isFalse();
        verify(orderRepository).insertIdempotencyKey(eq(userId), eq("key-1"), any(), eq(result.order().id()));
        verify(inventoryRepository).reserve("SKU-01", 2L);
    }

    @Test
    @DisplayName("Idempotency: request song song cùng key thắng trước -> rollback đơn của mình, trả đơn kia")
    void createOrder_concurrentDuplicate_returnsWinnerOrder() throws Exception {
        stubHappyPathForSku01();
        UUID winnerId = UUID.randomUUID();
        String hash = OrderServiceImpl.requestHash("WEB", addressId,
                new java.util.TreeMap<>(java.util.Map.of("SKU-01", 2L)));
        when(orderRepository.findIdempotencyRecord(userId, "key-1"))
                .thenReturn(java.util.Optional.empty())                       // lần kiểm tra đầu: chưa có
                .thenReturn(java.util.Optional.of(record(hash, winnerId)));   // sau khi va chạm khóa chính
        when(orderRepository.insertIdempotencyKey(any(), any(), any(), any())).thenReturn(0);
        when(orderRepository.findWithItemsById(winnerId)).thenReturn(java.util.Optional.of(existingOrder(winnerId)));

        CreateOrderResult result = orderService.createOrder(userId, "buyer@example.com", sku01x2(), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(winnerId);
        verify(inventoryRepository, never()).reserve(any(), anyLong());   // ném trước bước giữ kho
        verify(outboxEventRepository, never()).save(any());
        verify(mockLock).unlock();
    }

    private void stubHappyPathForSku01() throws Exception {
        AddressInfo addressInfo = new AddressInfo(
                addressId, userId, "Nguyen Van A", "0987654321", "123 Le Loi", "Ben Nghe", "Quan 1", "TP HCM", true);
        when(identityClient.getAddress(addressId)).thenReturn(new ClientApiResponse<>("SUCCESS", "OK", addressInfo));
        when(productClient.getSkuInfos(List.of("SKU-01")))
                .thenReturn(List.of(new SkuInfo("SKU-01", "Ao Polo", new BigDecimal("200000.00"), "ACTIVE")));
        when(redissonClient.getLock("lock:inventory:SKU-01")).thenReturn(mockLock);
        when(mockLock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        lenient().when(inventoryRepository.reserve("SKU-01", 2L)).thenReturn(1);
    }

    @Test
    @DisplayName("cancelOrder: không phải chủ đơn -> 404, không chạm trạng thái")
    void cancelOrder_notOwner_404() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(pendingOrder(orderId, UUID.randomUUID())));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, userId, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(orderStateService);
    }

    @Test
    @DisplayName("cancelOrder: đơn không còn PENDING -> 409 ORDER_NOT_CANCELLABLE")
    void cancelOrder_notPending_409() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(pendingOrder(orderId, userId)));
        when(orderStateService.cancelAndRelease(orderId, "UNPAID", "USER_CANCELLED")).thenReturn(false);

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, userId, false))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("cancelOrder: thành công -> giao cho OrderStateService, KHÔNG tự release kho, trả bản nạp lại")
    void cancelOrder_success_delegatesToStateService() {
        UUID orderId = UUID.randomUUID();
        Order cancelled = pendingOrder(orderId, userId);
        cancelled.setStatus("CANCELLED");
        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(pendingOrder(orderId, userId)));
        when(orderStateService.cancelAndRelease(orderId, "UNPAID", "USER_CANCELLED")).thenReturn(true);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(java.util.Optional.of(cancelled));

        OrderDto dto = orderService.cancelOrder(orderId, userId, false);

        assertThat(dto.status()).isEqualTo("CANCELLED");
        verify(inventoryRepository, never()).release(any(), anyLong());
        verify(orderRepository, never()).save(any());
    }
}
