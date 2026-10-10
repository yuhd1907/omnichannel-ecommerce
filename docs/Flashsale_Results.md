# Thử nghiệm flash sale — chống bán vượt kho

**Kịch bản:** 100 người mua cùng lúc, mỗi người mua 1 sản phẩm `FLASH-001`; kho có 10 sản phẩm. Kỳ vọng đúng 10 đơn thành công, 90 đơn bị từ chối, không có số âm, không có lỗi 500.

**Công cụ:** k6, [scripts/flashsale-k6.js](../scripts/flashsale-k6.js), executor `shared-iterations`, 100 VU × 100 iteration. Mỗi VU là một user thật, có token và địa chỉ riêng, được tạo trong `setup()`. Request gọi thẳng order-service (:8083), không qua gateway, để rate limit không chặn trước phần cần đo.

**Môi trường:** toàn bộ stack và k6 chạy trên cùng một máy bằng Docker Compose; order-service giới hạn 512 MB, Hikari pool 20, Redisson `tryLock` chờ tối đa 3 giây. Mỗi cấu hình chạy 1 lần; p95 chỉ dùng để so sánh tương đối giữa các cấu hình, không phải số đo năng lực hệ thống.

## Cách chạy lại

```powershell
# 1. Reset kho về 10 và xóa các đơn FLASH-001 cũ
Get-Content scripts\flashsale-reset.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db

# 2. Chạy k6 (Docker Desktop Windows: nếu --network host không tới được localhost
#    thì thay bằng: docker run --rm -i -e HOST=host.docker.internal grafana/k6 run -)
Get-Content scripts\flashsale-k6.js | docker run --rm -i --network host grafana/k6 run -

# 3. Kiểm tra: phải ra 0|10 và 10|10
Get-Content scripts\flashsale-verify.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db

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
