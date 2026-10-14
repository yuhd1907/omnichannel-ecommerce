# Thử nghiệm flash sale và load test

**Kịch bản:** 100 người mua cùng lúc, mỗi người mua 1 sản phẩm `FLASH-001`; kho có 10 sản phẩm. Kỳ vọng đúng 10 đơn thành công, 90 đơn bị từ chối, không có số âm, không có lỗi 500.

**Công cụ:** k6, [scripts/load-test/flashsale-k6.js](../scripts/load-test/flashsale-k6.js), executor `shared-iterations`, 100 VU × 100 iteration. Mỗi VU là một user thật, có token và địa chỉ riêng, được tạo trong `setup()`. Request gọi thẳng order-service (:8083), không qua gateway, để rate limit không chặn trước phần cần đo.

**Môi trường:** toàn bộ stack và k6 chạy trên cùng một máy bằng Docker Compose; order-service giới hạn 512 MB, Hikari pool 20, Redisson `tryLock` chờ tối đa 3 giây. Mỗi cấu hình chạy 1 lần; p95 chỉ dùng để so sánh tương đối giữa các cấu hình, không phải số đo năng lực hệ thống.

## Cách chạy lại

```powershell
# 1. Reset kho về 10 và xóa các đơn FLASH-001 cũ
Get-Content scripts\sql\flashsale-reset.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db

# 2. Chạy k6 (Docker Desktop Windows: nếu --network host không tới được localhost
#    thì thay bằng: docker run --rm -i -e HOST=host.docker.internal grafana/k6 run -)
Get-Content scripts\load-test\flashsale-k6.js | docker run --rm -i --network host grafana/k6 run -

# 3. Kiểm tra: phải ra 0|10 và 10|10
Get-Content scripts\sql\flashsale-verify.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db

# Trong lúc k6 chạy (terminal khác): quan sát khóa phân tán
docker exec -it ecommerce-redis redis-cli --scan --pattern "lock:inventory:*"
```

Tắt / bật lock (lần 2):

```powershell
$env:ORDER_INVENTORY_LOCK_ENABLED = 'false'; docker compose up -d --wait order-service
$env:ORDER_INVENTORY_LOCK_ENABLED = $null;   docker compose up -d --wait order-service   # bật lại
```

k6 tự đánh dấu **FAIL** nếu `created_201 ≠ 10` hoặc có bất kỳ mã nào khác 201/409 (`other_status > 0`).

## Kết quả (09/10/2026)

| Lần | Cấu hình | 201 | 409 OUT_OF_STOCK | 409 INVENTORY_BUSY | Mã khác | p95 `POST /orders` | `inventory` | Đơn / tổng SL | Khóa Redis | Lỗi chờ connection |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Lock **bật** | **10** | 90 | 0 | **0** | 781 ms | `0 \| 10` | `10 \| 10` | thấy `lock:inventory:FLASH-001` | 0 |
| 2 | Lock **tắt** | **10** | 90 | 0 | **0** | 738 ms | `0 \| 10` | `10 \| 10` | không có | 0 |
| 3 | Reserve ngây thơ (đọc → kiểm tra trong Java → `save`) | *chưa chạy* | | | | | | | | |

**Nhận xét**

- **Cả hai lớp đều đứng vững độc lập.** Tắt Redisson lock vẫn ra đúng 10 đơn, vì lớp chặn cuối là câu `UPDATE inventory ... WHERE available_qty >= :q` cùng ràng buộc `CHECK (available_qty >= 0)`: PostgreSQL khóa dòng khi cập nhật, request đến sau thấy số lượng đã giảm và cập nhật 0 dòng, nên đơn đó bị rollback với 409.
- **Lock không làm chậm đáng kể ở quy mô này.** p95 781 ms (bật) so với 738 ms (tắt). Lock tuần tự hóa phần giao dịch kho; giả thuyết là phần này ngắn so với các lời gọi trước lock (Feign lấy địa chỉ, gRPC lấy giá) nên ít ảnh hưởng tới p95. Chưa đo tách từng đoạn; muốn khẳng định cần log thời gian từng bước hoặc tracing. Không request nào phải chờ lock quá 3 giây (`INVENTORY_BUSY = 0`).
- **Lock có giá trị khi tranh chấp lớn hơn:** khi tắt lock, cả 100 request cùng mở transaction và tranh một dòng `inventory`, giữ connection trong lúc chờ khóa dòng. Ở 100 request với pool 20 thì chưa thấy lỗi chờ connection; tải lớn hơn hoặc pool nhỏ hơn sẽ làm lớp này lộ ra trước.

## Phát hiện phụ: lỗi 503 ở lần chạy đầu sau khi khởi động

Lần chạy đầu tiên (lock bật, ngay sau khi container order-service vừa được tạo lại) cho **10 × 201, 50 × 409, 40 × 503 `IDENTITY_SERVICE_UNAVAILABLE`**, p95 2,64 giây. Kho vẫn đúng `0 | 10`, `10 | 10`.

- **Nguyên nhân:** cả 40 lỗi đều là `'messageConverters' must not be empty` từ Spring Cloud OpenFeign. Feign khởi tạo `HttpMessageConverters` theo kiểu lazy và không thread-safe; loạt 100 request là lời gọi Feign đầu tiên sau khi khởi động, nên một phần thread thấy danh sách converter còn rỗng.
- **Không liên quan đến tải:** chạy lại khi service đã "ấm" thì 0 lỗi.
- **Trong thử nghiệm:** `setup()` của k6 gọi một đơn với SKU không tồn tại để khởi tạo Feign trước (trả 400, không đụng tới kho).
- **Còn tồn tại trong sản phẩm:** sau mỗi lần deploy, nếu đợt request đầu tiên đến đồng thời thì một phần sẽ nhận 503. Hướng sửa: gọi Feign một lần lúc khởi động (`ApplicationRunner`), hoặc nâng Spring Cloud (order-service đang dùng BOM `2025.1.2`, gateway dùng `2025.1.3`) nếu bản mới đã sửa.

---

## Load test thông lượng qua gateway (13/10/2026)

**Kịch bản:** [scripts/load-test/load-order-k6.js](../scripts/load-test/load-order-k6.js), executor `ramping-arrival-rate`.
- **Mức tải:** 50 → 100 → 500 → 1000 request/giây, giữ 1000 req/s trong 30 giây, rồi giảm về 0. Tổng 2 phút.
- **Request:** mỗi request là `POST /api/v1/orders` mua 1 `LOAD-001`, **đi qua API Gateway** (JWT + rate limit).
- **Người mua:** 300 user thật, được tạo trong `setup()`.
- **Kho ban đầu:** 1000.

**Môi trường:** toàn bộ stack (12 container) và k6 chạy trên cùng một laptop bằng Docker Desktop. Mỗi service giới hạn 512 MB. Rate limit được nâng tạm lên 200/400 cho đợt test. Chạy 1 lần; số tuyệt đối phụ thuộc máy, chỉ dùng để thấy hành vi của hệ thống.

### Kết quả

| Chỉ số | Giá trị |
| --- | --- |
| Request đặt hàng | **66.784** |
| `201` tạo đơn thành công | **1.000** (đúng bằng kho) |
| `409 OUT_OF_STOCK` | 64.574 |
| `409 INVENTORY_BUSY` (chờ lock quá 3 giây) | 1.145 |
| Lỗi khác | 65 (các mẫu log đều là timeout 10 giây phía k6, status 0); không có `429` |
| Tải đỉnh giữ được | **1000 req/s** trong 30 giây, chỉ khoảng 10 VU đang chờ cùng lúc |
| Độ trễ | trung vị **10 ms**, p90 161 ms, p95 971 ms, p99 5,8 s |
| `dropped_iterations` | 1.215 (1,8%), đều ở giai đoạn còn hàng |
| Kiểm tra kho (`load-verify.sql`) | `0 \| 1000`; 1000 đơn `PENDING`; **cả 4 điều kiện `t`**: không âm, giữ khớp đơn, bảo toàn kho, không bán vượt |

Threshold `other_status == 0` của k6 báo **FAIL** vì 65 timeout ở trên; threshold được giữ nguyên để không che lỗi.

### Đọc kết quả: hai giai đoạn khác hẳn nhau

**1. Giai đoạn còn hàng (khoảng 35 giây đầu): bán được khoảng 30 đơn/giây.**

Mọi request đều mua cùng một SKU nên đi tuần tự qua khóa Redisson `lock:inventory:LOAD-001` và khóa dòng `inventory`. Mỗi đơn chỉ giữ khóa trong transaction ngắn; lời gọi Identity (Feign) và Product (gRPC) đã làm **trước** khi lấy khóa. Dù vậy, 1000 đơn vẫn mất khoảng 35 giây.

Tải lúc đó (50 → 300 req/s) vượt tốc độ này, nên request xếp hàng chờ khóa:
- chờ quá 3 giây → `409 INVENTORY_BUSY`, client biết để thử lại;
- một số ít vượt 10 giây → k6 cắt (65 timeout);
- đuôi p95/p99 và `dropped_iterations` đều đến từ giai đoạn này.

**2. Giai đoạn hết hàng (phần còn lại): giữ được 1000 req/s, trung vị 10 ms.**

Bước *từ chối sớm* chỉ đọc tồn kho rồi trả `409`, không gọi Identity/Product và không lấy khóa. Ở 1000 req/s mà chỉ khoảng 10 request đang chờ cùng lúc, tức hệ thống còn dư sức. Đây đúng là tình huống flash sale thật: phần lớn lượt bấm "mua" đến sau khi hàng đã hết.

**3. Tính đúng không đổi dưới tải:** không âm kho, không bán vượt (đúng 1000 đơn cho 1000 hàng), lượng hàng đang giữ khớp với tổng số lượng của các đơn `PENDING`.

### Giới hạn và hướng cải thiện

- **Một SKU nóng bị tuần tự hóa** là đánh đổi có chủ đích: chọn đúng trước, nhanh sau. Có thể tăng tốc theo các hướng:
  - thử **tắt Redisson lock** (lần 2 ở bảng trên cho thấy câu `UPDATE` có điều kiện một mình vẫn đúng), bớt các vòng gọi Redis;
  - **chia kho một SKU thành nhiều dòng** (bucket) để nhiều transaction chạy song song;
  - **xếp hàng đơn qua queue** và trả kết quả bất đồng bộ.
- k6 chạy cùng máy với hệ thống nên tranh CPU; số đo trên server riêng sẽ khác.
- 1000 đơn `PENDING` sau test sẽ được job hủy sau 15 phút và trả kho. Muốn chạy lại thì chạy `load-reset.sql`.
