package ra.edu.orderservice.service.impl;


import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ra.edu.common.response.ErrorCode;
import ra.edu.orderservice.client.IdentityClient;
import ra.edu.orderservice.client.ProductClient;
import ra.edu.orderservice.client.dto.AddressInfo;
import ra.edu.orderservice.client.dto.ClientApiResponse;
import ra.edu.orderservice.client.dto.SkuInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import ra.edu.common.response.PageData;
import ra.edu.orderservice.dto.OrderDto;
import ra.edu.orderservice.dto.OrderSummaryDto;
import ra.edu.orderservice.dto.request.CreateOrderRequest;
import ra.edu.orderservice.entity.Order;
import ra.edu.orderservice.entity.OrderItem;
import ra.edu.orderservice.entity.OutboxEvent;
import ra.edu.orderservice.exception.BusinessException;
import ra.edu.orderservice.repository.InventoryRepository;
import ra.edu.orderservice.repository.OrderRepository;
import ra.edu.orderservice.repository.OutboxEventRepository;
import ra.edu.orderservice.service.OrderService;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Set<String> VALID_CHANNELS = Set.of("WEB", "MOBILE");

    private final OrderRepository        orderRepository;
    private final InventoryRepository    inventoryRepository;
    private final OutboxEventRepository  outboxEventRepository;
    private final ProductClient          productClient;
    private final IdentityClient         identityClient;
    private final TransactionTemplate    transactionTemplate;
    private final RedissonClient         redissonClient;

    @Value("${order.inventory-lock.enabled:true}")
    private boolean lockEnabled = true;

    @Value("${order.inventory-lock.wait-time-seconds:3}")
    private long waitTimeSeconds = 3;

    @Override
    public OrderDto createOrder(UUID userId, CreateOrderRequest request) {

        // ── 1. Validate channel (ngoài transaction) ─────────────────────────
        String channel = request.channel().toUpperCase();
        if (!VALID_CHANNELS.contains(channel)) {
            throw new BusinessException(
                    "INVALID_CHANNEL",
                    "Channel must be WEB or MOBILE, got: " + request.channel(),
                    HttpStatus.BAD_REQUEST);
        }

        // ── 2. Gọi Identity lấy địa chỉ + verify ownership (HTTP - ngoài tx) ─
        AddressInfo address = fetchAddress(request.addressId(), userId);

        // ── 3. Gộp items trùng SKU, sắp xếp theo skuCode tránh deadlock ─────
        Map<String, Long> skuQuantityMap = request.items().stream()
                .collect(Collectors.toMap(
                        CreateOrderRequest.OrderItemRequest::skuCode,
                        CreateOrderRequest.OrderItemRequest::quantity,
                        Long::sum,          // gộp quantity nếu trùng skuCode
                        TreeMap::new));     // TreeMap: sort lexicographic, cố định thứ tự

        // ── 4. Gọi Product lấy thông tin từng SKU (gRPC - ngoài tx) ─────────
        Map<String, SkuInfo> skuInfoMap = fetchSkuInfos(skuQuantityMap.keySet());

        // ── 5. Build order + items trong bộ nhớ (ngoài tx) ───────────────────
        String shippingAddress = buildShippingAddress(address);

        Order order = Order.builder()
                .userId(userId)
                .channel(channel)
                .shippingRecipientName(address.recipientName())
                .shippingPhone(address.phone())
                .shippingAddress(shippingAddress)
                .build();

        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map.Entry<String, Long> entry : skuQuantityMap.entrySet()) {
            String  skuCode  = entry.getKey();
            long    quantity = entry.getValue();
            SkuInfo sku      = skuInfoMap.get(skuCode);

            // Kiểm tra SKU còn active
            if (!"ACTIVE".equalsIgnoreCase(sku.status())) {
                throw new BusinessException(
                        "SKU_INACTIVE",
                        "SKU is not available for purchase: " + skuCode,
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            BigDecimal subtotal = sku.price().multiply(BigDecimal.valueOf(quantity));
            totalAmount = totalAmount.add(subtotal);

            items.add(OrderItem.builder()
                    .order(order)
                    .skuCode(skuCode)
                    .productName(sku.productName())
                    .unitPrice(sku.price())
                    .quantity(quantity)
                    .subtotal(subtotal)
                    .build());
        }

        order.setTotalAmount(totalAmount);
        order.setItems(items);

        // ── 6. Lấy Redisson distributed lock cho mọi SKU theo thứ tự đã sort ──
        RLock lock = null;
        if (lockEnabled) {
            lock = getLockForSkus(skuQuantityMap.keySet());
            try {
                // tryLock(waitTime, TimeUnit) không truyền leaseTime để kích hoạt Redisson Watchdog
                boolean acquired = lock.tryLock(waitTimeSeconds, TimeUnit.SECONDS);
                if (!acquired) {
                    log.warn("Could not acquire inventory lock for skus={} within {}s", skuQuantityMap.keySet(), waitTimeSeconds);
                    throw new BusinessException(
                            ErrorCode.INVENTORY_BUSY,
                            "Sản phẩm đang có nhiều người mua, vui lòng thử lại",
                            HttpStatus.CONFLICT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted while acquiring inventory lock for skus={}", skuQuantityMap.keySet(), e);
                throw new BusinessException(
                        ErrorCode.INVENTORY_BUSY,
                        "Sản phẩm đang có nhiều người mua, vui lòng thử lại",
                        HttpStatus.CONFLICT);
            }
        }

        try {
            // ── 7. Persist order + reserve inventory + outbox trong 1 Transaction ngắn ───
            //    Dùng TransactionTemplate để ranh giới transaction nằm trọn vẹn SAU các cuộc
            //    gọi mạng (HTTP/gRPC) và TRƯỚC khi nhả lock (tránh release lock trước khi commit).
            Order savedOrder = transactionTemplate.execute(status -> {
                Order persistedOrder = orderRepository.save(order);

                // Reserve inventory: conditional UPDATE cho từng SKU
                for (Map.Entry<String, Long> entry : skuQuantityMap.entrySet()) {
                    String skuCode  = entry.getKey();
                    long   quantity = entry.getValue();

                    int rowCount = inventoryRepository.reserve(skuCode, quantity);
                    if (rowCount == 0) {
                        // DB từ chối vì available_qty < quantity → rollback transaction
                        log.warn("Out of stock: sku={}, requested={}, orderId={}", skuCode, quantity, persistedOrder.getId());
                        throw new BusinessException(
                                "OUT_OF_STOCK",
                                "Insufficient inventory for SKU: " + skuCode,
                                HttpStatus.CONFLICT);
                    }
                }

                // Ghi outbox event (cùng transaction)
                String payload = buildOutboxPayload(persistedOrder, skuQuantityMap);
                OutboxEvent outbox = OutboxEvent.builder()
                        .aggregateType("Order")
                        .aggregateId(persistedOrder.getId())
                        .eventType("ORDER_CREATED")
                        .payload(payload)
                        .build();
                outboxEventRepository.save(outbox);

                return persistedOrder;
            });

            log.info("Order created: id={}, userId={}, total={}", savedOrder.getId(), userId, totalAmount);
            return toDto(savedOrder);
        } finally {
            // ── 8. Nhả lock trong finally sau khi transaction đã commit hoàn tất ──
            if (lock != null && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (IllegalMonitorStateException e) {
                    log.warn("Failed to unlock inventory lock: {}", e.getMessage());
                }
            }
        }
    }

    private RLock getLockForSkus(Set<String> skuCodes) {
        if (skuCodes == null || skuCodes.isEmpty()) {
            return null;
        }
        RLock[] locks = skuCodes.stream()
                .map(sku -> redissonClient.getLock("lock:inventory:" + sku))
                .toArray(RLock[]::new);

        return locks.length == 1 ? locks[0] : redissonClient.getMultiLock(locks);
    }

    // ─────────────────────────── private helpers ─────────────────────────────

    private AddressInfo fetchAddress(UUID addressId, UUID userId) {
        ClientApiResponse<AddressInfo> response;
        try {
            response = identityClient.getAddress(addressId);
        } catch (FeignException.NotFound e) {
            throw new BusinessException("ADDRESS_NOT_FOUND",
                    "Address not found: " + addressId, HttpStatus.BAD_REQUEST);
        } catch (FeignException e) {
            log.error("identity-service unavailable: {}", e.getMessage());
            throw new BusinessException("IDENTITY_SERVICE_UNAVAILABLE",
                    "Could not verify address, please try again", HttpStatus.SERVICE_UNAVAILABLE);
        }

        AddressInfo address = response.data();
        if (address == null) {
            throw new BusinessException("ADDRESS_NOT_FOUND",
                    "Address not found: " + addressId, HttpStatus.BAD_REQUEST);
        }
        // Verify ownership — địa chỉ phải thuộc về user đang đặt hàng
        if (!userId.equals(address.userId())) {
            throw new BusinessException("ADDRESS_ACCESS_DENIED",
                    "Address does not belong to current user", HttpStatus.FORBIDDEN);
        }
        return address;
    }

    private Map<String, SkuInfo> fetchSkuInfos(Set<String> skuCodes) {
        List<SkuInfo> skuInfos = productClient.getSkuInfos(new ArrayList<>(skuCodes));
        Map<String, SkuInfo> result = new HashMap<>();
        for (SkuInfo sku : skuInfos) {
            result.put(sku.skuCode(), sku);
        }
        for (String skuCode : skuCodes) {
            if (!result.containsKey(skuCode)) {
                throw new BusinessException("SKU_UNAVAILABLE",
                        "Product SKU not found: " + skuCode, HttpStatus.BAD_REQUEST);
            }
        }
        return result;
    }

    /** Gộp các trường địa chỉ thành chuỗi text lưu snapshot vào đơn hàng. */
    private String buildShippingAddress(AddressInfo a) {
        return String.join(", ",
                a.addressLine(),
                a.ward() != null ? a.ward() : "",
                a.district() != null ? a.district() : "",
                a.city()).replaceAll(",\\s*,", ",").stripTrailing().replaceAll(",$", "");
    }

    private String buildOutboxPayload(Order order, Map<String, Long> skuQuantityMap) {
        // Build JSON thủ công — tránh phụ thuộc Jackson chỉ cho outbox payload đơn giản
        StringBuilder skusJson = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> e : skuQuantityMap.entrySet()) {
            if (!first) skusJson.append(',');
            skusJson.append('"').append(e.getKey()).append('"').append(':').append(e.getValue());
            first = false;
        }
        skusJson.append('}');

        return '{'
                + "\"orderId\":\""   + order.getId()         + '"'
                + ",\"userId\":\""    + order.getUserId()     + '"'
                + ",\"channel\":\""   + order.getChannel()    + '"'
                + ",\"totalAmount\":" + order.getTotalAmount()
                + ",\"skus\":"        + skusJson
                + '}';
    }

    /** Build JSON 2 trường cho outbox cancel payload. */
    private String buildCancelPayload(Order order) {
        return '{'
                + "\"orderId\":\"" + order.getId()     + '"'
                + ",\"userId\":\"" + order.getUserId() + '"'
                + '}';
    }

    private OrderDto toDto(Order order) {
        List<OrderDto.OrderItemDto> itemDtos = order.getItems().stream()
                .map(i -> new OrderDto.OrderItemDto(
                        i.getId(),
                        i.getSkuCode(),
                        i.getProductName(),
                        i.getUnitPrice(),
                        i.getQuantity(),
                        i.getSubtotal()))
                .toList();

        return new OrderDto(
                order.getId(),
                order.getUserId(),
                order.getChannel(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getShippingRecipientName(),
                order.getShippingPhone(),
                order.getShippingAddress(),
                order.getCreatedAt(),
                itemDtos);
    }

    private OrderSummaryDto toSummaryDto(Order order) {
        return new OrderSummaryDto(
                order.getId(),
                order.getChannel(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getCreatedAt());
    }

    // ─────────────────────────── new public methods ──────────────────────────

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID orderId, UUID userId, boolean isAdmin) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new BusinessException(
                        "ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        // Nếu không phải admin và không phải chủ đơn → trả 404 (không lộ sự tồn tại)
        if (!isAdmin && !userId.equals(order.getUserId())) {
            throw new BusinessException(
                    "ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND);
        }

        return toDto(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageData<OrderSummaryDto> listMyOrders(UUID userId, Pageable pageable) {
        // Ép sort created_at DESC bất kể client gửi gì
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> page = orderRepository.findByUserId(userId, sorted);
        return new PageData<>(
                page.getContent().stream().map(this::toSummaryDto).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional
    public OrderDto cancelOrder(UUID orderId, UUID userId, boolean isAdmin) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new BusinessException(
                        "ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        // Ownership check — 404 thay vì 403
        if (!isAdmin && !userId.equals(order.getUserId())) {
            throw new BusinessException(
                    "ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND);
        }

        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(
                    "CANNOT_CANCEL",
                    "Only PENDING orders can be cancelled, current status: " + order.getStatus(),
                    HttpStatus.CONFLICT);
        }

        // Hoàn kho: release từng SKU trong cùng transaction
        for (OrderItem item : order.getItems()) {
            int rowCount = inventoryRepository.release(item.getSkuCode(), item.getQuantity());
            if (rowCount == 0) {
                // Không nên xảy ra nếu data nhất quán — log rõ để điều tra
                log.error("Release inventory failed: sku={}, qty={}, orderId={}",
                        item.getSkuCode(), item.getQuantity(), orderId);
                throw new BusinessException(
                        "INVENTORY_RELEASE_FAILED",
                        "Could not release inventory for SKU: " + item.getSkuCode(),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        order.setStatus("CANCELLED");

        // Ghi outbox event ORDER_CANCELLED
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(order.getId())
                .eventType("ORDER_CANCELLED")
                .payload(buildCancelPayload(order))
                .build());

        log.info("Order cancelled: id={}, userId={}", orderId, userId);
        return toDto(orderRepository.save(order));
    }
}
