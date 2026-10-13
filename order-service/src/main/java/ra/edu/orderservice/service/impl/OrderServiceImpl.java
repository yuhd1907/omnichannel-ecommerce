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
import ra.edu.orderservice.dto.CreateOrderResult;
import ra.edu.orderservice.dto.OrderDto;
import ra.edu.orderservice.dto.OrderSummaryDto;
import ra.edu.orderservice.dto.request.CreateOrderRequest;
import ra.edu.orderservice.entity.Inventory;
import ra.edu.orderservice.entity.Order;
import ra.edu.orderservice.entity.OrderItem;
import ra.edu.orderservice.entity.OutboxEvent;
import ra.edu.orderservice.exception.BusinessException;
import ra.edu.orderservice.repository.InventoryRepository;
import ra.edu.orderservice.repository.OrderRepository;
import ra.edu.orderservice.repository.OutboxEventRepository;
import ra.edu.orderservice.service.OrderService;
import ra.edu.orderservice.service.OrderStateService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final OrderStateService      orderStateService;

    @Value("${order.inventory-lock.enabled:true}")
    private boolean lockEnabled = true;

    @Value("${order.inventory-lock.wait-time-seconds:3}")
    private long waitTimeSeconds = 3;

    @Override
    public CreateOrderResult createOrder(UUID userId, String customerEmail, CreateOrderRequest request,
                                         String idempotencyKey) {

        // ── 1. Validate channel (ngoài transaction) ─────────────────────────
        String channel = request.channel().toUpperCase();
        if (!VALID_CHANNELS.contains(channel)) {
            throw new BusinessException(
                    "INVALID_CHANNEL",
                    "Channel must be WEB or MOBILE, got: " + request.channel(),
                    HttpStatus.BAD_REQUEST);
        }

        // ── 2. Gộp items trùng SKU, sắp xếp theo skuCode tránh deadlock ─────
        Map<String, Long> skuQuantityMap = request.items().stream()
                .collect(Collectors.toMap(
                        CreateOrderRequest.OrderItemRequest::skuCode,
                        CreateOrderRequest.OrderItemRequest::quantity,
                        Long::sum,          // gộp quantity nếu trùng skuCode
                        TreeMap::new));     // TreeMap: sort lexicographic, cố định thứ tự

        // ── 2b. Idempotency-Key: key đã dùng thì trả lại đơn cũ, không làm gì thêm ─────
        //    Kiểm tra TRƯỚC mọi lời gọi mạng và trước bước từ chối sớm: client gửi lại sau khi
        //    hàng đã hết vẫn phải nhận lại đúng đơn đã đặt thành công, không phải 409.
        String requestHash = idempotencyKey == null ? null
                : requestHash(channel, request.addressId(), skuQuantityMap);
        if (idempotencyKey != null) {
            Optional<CreateOrderResult> replay = findReplay(userId, idempotencyKey, requestHash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        // ── 3. Từ chối sớm khi CHẮC CHẮN hết hàng (đọc không khóa) ─────────
        //    Flash sale: phần lớn request đến sau khi kho về 0. Chặn ở đây thì không tốn
        //    1 HTTP (identity) + 1 gRPC (product) + lock Redis cho mỗi request vô vọng.
        //    Không thay thế bước reserve có điều kiện ở dưới — đó mới là chốt đúng/sai.
        rejectIfSurelyOutOfStock(skuQuantityMap);

        // ── 4. Gọi Identity lấy địa chỉ + verify ownership (HTTP - ngoài tx) ─
        AddressInfo address = fetchAddress(request.addressId(), userId);

        // ── 5. Gọi Product lấy thông tin từng SKU (gRPC - ngoài tx) ─────────
        Map<String, SkuInfo> skuInfoMap = fetchSkuInfos(skuQuantityMap.keySet());

        // ── 6. Build order + items trong bộ nhớ (ngoài tx) ───────────────────
        String shippingAddress = buildShippingAddress(address);

        Order order = Order.builder()
                .userId(userId)
                .customerEmail(customerEmail)
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

        // ── 7. Lấy Redisson distributed lock cho mọi SKU theo thứ tự đã sort ──
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
            // ── 8. Persist order + reserve inventory + outbox trong 1 Transaction ngắn ───
            //    Dùng TransactionTemplate để ranh giới transaction nằm trọn vẹn SAU các cuộc
            //    gọi mạng (HTTP/gRPC) và TRƯỚC khi nhả lock (tránh release lock trước khi commit).
            Order savedOrder = transactionTemplate.execute(status -> {
                Order persistedOrder = orderRepository.save(order);

                // Ghi Idempotency-Key ngay sau khi có id đơn, TRƯỚC khi khóa dòng kho: request trùng
                // key chạy song song sẽ chờ ở đây (khóa chính) chứ không giữ khóa kho của SKU.
                if (idempotencyKey != null && orderRepository.insertIdempotencyKey(
                        userId, idempotencyKey, requestHash, persistedOrder.getId()) == 0) {
                    throw new DuplicateIdempotencyKey();   // rollback đơn vừa ghi
                }

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
            return new CreateOrderResult(toDto(savedOrder), false);
        } catch (DuplicateIdempotencyKey e) {
            // Request khác cùng key đã commit trong lúc request này đang xử lý -> trả đơn của nó.
            log.info("Concurrent request with same Idempotency-Key, returning existing order (userId={})", userId);
            return findReplay(userId, idempotencyKey, requestHash)
                    .orElseThrow(() -> new IllegalStateException("Idempotency key vanished: " + idempotencyKey));
        } finally {
            // ── 9. Nhả lock trong finally sau khi transaction đã commit hoàn tất ──
            if (lock != null && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (IllegalMonitorStateException e) {
                    log.warn("Failed to unlock inventory lock: {}", e.getMessage());
                }
            }
        }
    }

    /** Báo hiệu key đã được request khác ghi; ném trong transaction để rollback đơn của request này. */
    private static final class DuplicateIdempotencyKey extends RuntimeException {
        DuplicateIdempotencyKey() {
            super(null, null, false, false);   // không cần stack trace
        }
    }

    /**
     * Key đã dùng: cùng nội dung -> trả lại đơn cũ; khác nội dung -> 422 (client dùng lại key
     * cho một đơn khác, nhiều khả năng là bug phía client).
     */
    private Optional<CreateOrderResult> findReplay(UUID userId, String idempotencyKey, String requestHash) {
        return orderRepository.findIdempotencyRecord(userId, idempotencyKey).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new BusinessException(
                        ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key was already used for a different order request",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            Order existing = orderRepository.findWithItemsById(record.getOrderId())
                    .orElseThrow(() -> new IllegalStateException("Order of idempotency key not found: " + record.getOrderId()));
            log.info("Idempotent replay: key reused, returning order {} (userId={})", existing.getId(), userId);
            return new CreateOrderResult(toDto(existing), true);
        });
    }

    /** SHA-256 của nội dung đơn đã chuẩn hóa (SKU đã gộp và sắp xếp) — so khớp request gửi lại. */
    public static String requestHash(String channel, UUID addressId, Map<String, Long> skuQuantityMap) {
        String canonical = channel + "|" + addressId + "|" + skuQuantityMap;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
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

    private void rejectIfSurelyOutOfStock(Map<String, Long> skuQuantityMap) {
        for (Inventory inv : inventoryRepository.findBySkuCodeIn(skuQuantityMap.keySet())) {
            Long requested = skuQuantityMap.get(inv.getSkuCode());
            if (requested != null && inv.getAvailableQty() < requested) {
                throw new BusinessException(
                        ErrorCode.OUT_OF_STOCK,
                        "Insufficient inventory for SKU: " + inv.getSkuCode(),
                        HttpStatus.CONFLICT);
            }
        }
        // SKU chưa có dòng inventory: để bước reserve quyết định như cũ.
    }

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
                + ",\"customerEmail\":" + jsonString(order.getCustomerEmail())
                + ",\"channel\":\""   + order.getChannel()    + '"'
                + ",\"totalAmount\":" + order.getTotalAmount()
                + ",\"skus\":"        + skusJson
                + '}';
    }

    /** Chuỗi JSON có escape, hoặc null. Email đã được identity validate nhưng không tin tuyệt đối. */
    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(
                        "ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        // Ownership check — 404 thay vì 403
        if (!isAdmin && !userId.equals(order.getUserId())) {
            throw new BusinessException(
                    "ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND);
        }

        // KHÔNG tự kiểm status trong Java rồi setStatus: thanh toán thành công có thể tới đúng lúc
        // này. cancelAndRelease chuyển trạng thái bằng UPDATE ... WHERE status='PENDING'.
        if (!orderStateService.cancelAndRelease(orderId, "UNPAID", "USER_CANCELLED")) {
            throw new BusinessException(
                    ErrorCode.ORDER_NOT_CANCELLABLE,
                    "Only PENDING orders can be cancelled",
                    HttpStatus.CONFLICT);
        }

        log.info("Order cancelled by {}: id={}", isAdmin ? "admin" : "owner", orderId);
        return toDto(orderRepository.findWithItemsById(orderId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public PageData<OrderSummaryDto> listAllOrders(String status, Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> page = (status == null || status.isBlank())
                ? orderRepository.findAll(sorted)
                : orderRepository.findByStatus(status.trim().toUpperCase(), sorted);

        return new PageData<>(
                page.getContent().stream().map(this::toSummaryDto).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public OrderDto updateStatusByAdmin(UUID orderId, String targetStatus) {
        // Transaction nằm trong OrderStateService; ở đây chỉ map sang DTO (items đã nạp sẵn).
        return toDto(orderStateService.advanceFulfillment(orderId, targetStatus));
    }
}
