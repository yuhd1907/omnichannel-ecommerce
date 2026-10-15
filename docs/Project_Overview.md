# Tổng quan dự án — đã làm được gì, chưa làm được gì

Tài liệu mở đầu buổi demo (khoảng 3–4 phút). Chi tiết từng điều chỉnh so với đề bài: [Scope_Adjustments.md](Scope_Adjustments.md). Kịch bản demo: [Demo_Script.md](Demo_Script.md).

## 1. Bài toán

Hệ thống thương mại điện tử **đa kênh** (Web / Mobile) theo kiến trúc **microservices**. Người dùng đăng ký, tìm và xem sản phẩm, đặt hàng, thanh toán và nhận thông báo; admin quản lý sản phẩm, tồn kho và giao hàng.

Yêu cầu kỹ thuật trọng tâm:
- **Không bán vượt kho** khi nhiều người tranh mua (flash sale).
- **Dữ liệu nhất quán** giữa các service khi mỗi service có database riêng.
- **Chịu lỗi** khi một thành phần chết.

![Kiến trúc](architecture.png)

## 2. Con số chính

| | |
| --- | --- |
| Service | 6 Spring Boot service (gateway + 5 nghiệp vụ) + 2 module dùng chung + web Next.js |
| Database | 5 PostgreSQL (database per service), schema quản lý bằng Flyway |
| API | Hơn 30 endpoint REST, 2 RPC gRPC, spec OpenAPI 3.1 sinh từ code (`docs/api/`) |
| Event | 8 loại event qua RabbitMQ (5 `order.*`, 2 `payment.*`, 1 `product.*`), mỗi queue có DLQ |
| Kiểm thử | 145 unit test chạy trong CI + 39 integration test; smoke test end-to-end (`scripts/smoke-test.mjs`); 2 kịch bản k6 |
| Load test | Tải mục tiêu **1000 req/s** đặt hàng qua gateway: giữ đúng (66.784 request, kho 1000 → đúng 1000 đơn, không bán vượt), từ chối khi hết hàng trung vị 10 ms. Đơn thành công khoảng 30/giây cho một SKU nóng; **chưa chứng minh** 500–1.000 đơn thành công/giây |
| Triển khai | VPS thật, HTTPS, CI/CD tự động: https://huyecommerce.duckdns.org |

## 3. Đã làm được

### 3.1 Nghiệp vụ

| Chức năng | Service | Xem trong demo |
| --- | --- | --- |
| Đăng ký, đăng nhập, refresh token, phân quyền `USER` / `ADMIN` | identity | Web (modal đăng nhập), trang Quản trị |
| Địa chỉ giao hàng | identity | Web (trang đặt hàng) |
| Danh mục, sản phẩm, SKU biến thể (JSONB), admin CRUD | product | Web (cửa hàng, chi tiết sản phẩm) |
| Tìm kiếm toàn văn (`tsvector`, không dấu, gõ dở vẫn khớp) | product | Demo 2b |
| Đặt hàng, giữ hàng, xem / hủy đơn; `Idempotency-Key` chống tạo đơn trùng khi gửi lại | order | S1, demo 2b |
| **Thanh toán khi nhận hàng (COD)**: đơn xác nhận ngay, giao xong mới ghi nhận đã thu tiền; **VNPAY sandbox** (thẻ / VNPAY-QR, IPN ký HMAC-SHA512) | payment, order | S1, S2, S3 |
| Tự hủy đơn không thanh toán sau 15 phút, trả kho | order | (nói, không demo) |
| Vòng đời giao hàng `CONFIRMED → SHIPPING → COMPLETED` cho admin | order | S3 |
| Quản trị tồn kho (nhập / điều chỉnh); SKU mới tự có dòng kho qua event `product.sku.created` | order | Web (Quản trị → Tồn kho) |
| Email cho mọi bước của đơn (5 loại) | notification | Mailpit |

### 3.2 Kiến trúc phân tán

- **API Gateway** là cửa vào duy nhất: xác thực JWT, rate limit bằng Redis (theo user hoặc IP, chống giả `X-Forwarded-For`).
- **Giao tiếp đồng bộ:**
  - Order → Product qua **gRPC** (lấy giá, trạng thái SKU);
  - Order → Identity qua **REST/Feign** (địa chỉ giao hàng).
- **Saga điều phối bằng sự kiện** qua RabbitMQ: đặt hàng → thanh toán → xác nhận / hủy + trả kho → thông báo.
- **Transactional Outbox** ở Order, Payment và Product: event ghi cùng transaction với dữ liệu, relay gửi đi có publisher confirm. RabbitMQ chết vẫn nhận đơn, broker sống lại thì event tự gửi tiếp. Relay nhận lô bằng `FOR UPDATE SKIP LOCKED` nên **chạy được nhiều instance** mà không gửi trùng.
- **Consumer idempotent:**
  - notification: bảng `processed_messages`;
  - payment: `order_id UNIQUE` và `gateway_txn_id UNIQUE` chống webhook lặp.
- **Dead Letter Queue** cho message lỗi, retry có backoff.

### 3.3 Tính đúng đắn khi tranh chấp

- Giữ hàng bằng `UPDATE ... WHERE available_qty >= :q` cùng `CHECK (available_qty >= 0)`: database tự đảm bảo không âm kho.
- **Redisson lock** theo `sku_code` giảm tranh chấp trước khi vào transaction.
- Mọi chuyển trạng thái đơn đều là `UPDATE ... WHERE status = :from`. Hủy đơn và thanh toán tới cùng lúc thì chỉ một bên thắng.
- **Đã kiểm chứng:** 100 người mua cùng lúc, kho 10 → đúng 10 đơn, 90 nhận 409, kho `0 | 10`, cả khi bật lẫn tắt lock ([Flashsale_Results.md](Flashsale_Results.md)).
- Hết hàng thì từ chối sớm, không gọi identity/product/Redis, để chịu tải flash sale.
- **`Idempotency-Key`** cho `POST /orders`: bảng `idempotency_keys` (khóa chính `user_id + key`) ghi trong cùng transaction với đơn. Gửi lại cùng key → trả đúng đơn cũ (200, `Idempotent-Replayed: true`), không giữ kho lần hai; hai request trùng key chạy song song thì một bên chờ ở khóa chính rồi nhận đơn của bên kia; dùng lại key cho nội dung khác → 422.
- **Load test qua gateway:** tăng dần tới 1000 req/s.
  - Kết quả: đúng 1000 đơn cho kho 1000, 4 điều kiện bảo toàn kho đều đạt.
  - Giai đoạn hết hàng giữ 1000 req/s với trung vị 10 ms (gần như toàn bộ là `409 OUT_OF_STOCK` trả sớm).
  - Giai đoạn còn hàng bán khoảng 30 đơn/giây cho một SKU nóng, vì các đơn phải đi tuần tự qua khóa. Đây là đánh đổi chọn tính đúng ([chi tiết](Flashsale_Results.md#load-test-thông-lượng-qua-gateway-13102026)).
  - **Phạm vi kết luận:** hệ thống giữ được tính đúng đắn dưới tải mục tiêu 1.000 request/giây và xử lý nhanh đường từ chối khi hết hàng; chưa chứng minh được khả năng tạo 500–1.000 đơn thành công mỗi giây. Lần chạy còn 65 timeout và threshold `other_status == 0` của k6 FAIL.

### 3.4 Bảo mật

- JWT HS512. Gateway xác thực token; từng service kiểm tra lại token và phân quyền `USER` / `ADMIN` (không tin gateway tuyệt đối).
- User không xem được đơn của người khác: trả 404 thay vì 403 để không lộ đơn có tồn tại.
- Webhook MOCK ký **HMAC-SHA256**, VNPAY ký **HMAC-SHA512**; đều kiểm tra khớp số tiền và chống xử lý lặp bằng `gateway_txn_id UNIQUE`.
- Trên VPS chỉ mở cổng 22 / 80 / 443 (đã quét nmap từ ngoài). DB, Redis, RabbitMQ, Grafana chỉ bind `127.0.0.1`. Nginx chặn `/actuator/*` trừ health.

### 3.5 Vận hành và DevOps

- Docker multi-stage, chạy toàn hệ thống bằng 1 lệnh `docker compose up`.
- **CI/CD:** GitHub Actions chạy test Java + kiểm tra/build frontend → build 7 image → push GHCR (tag `sha-<commit>`) → job *Deploy to VPS* dùng `appleboy/ssh-action` SSH vào VPS, deploy đúng tag đó, chờ health check, lỗi thì tự quay lại bản trước.
- **HTTPS** Let's Encrypt (Certbot, tự gia hạn) trên tên miền thật; UFW chỉ mở 22/80/443, SSH chỉ dùng key, fail2ban.
- **Giám sát (đủ 3 trụ cột):** metrics Prometheus + Grafana (request/giây, độ trễ p95, mã lỗi, heap JVM, connection DB, queue RabbitMQ); log tập trung **Loki + Promtail**; trace phân tán **OpenTelemetry → Jaeger** qua gateway, REST, Feign, gRPC, RabbitMQ. Mọi response có `traceId`, tra được log và trace của đúng request.
- **Web:** frontend Next.js 16 (`frontend/`) cho khách và admin, chạy sau Nginx cùng tên miền.
- **Tài liệu:** thiết kế DDD + API contract, ERD 5 database, sơ đồ kiến trúc và RabbitMQ, hướng dẫn vận hành.

## 4. Chưa làm được / làm khác đề bài

| Hạng mục | Hiện trạng | Lý do chính |
| --- | --- | --- |
| **App Mobile** | Chưa có; đã có **web Next.js** cho khách và admin. API đã nhận `channel=MOBILE` | Web đủ để demo luồng; app mobile dùng lại toàn bộ API |
| **Cổng thanh toán thật** | VNPAY **sandbox** qua `VnpayAdapter` (cần đăng ký TMN code); chưa có MoMo / QR | Môi trường thật cần hợp đồng merchant |
| **Hoàn tiền tự động** | Đánh dấu `needs_refund`, đối soát thủ công | Mỗi cổng có API hoàn tiền riêng, là một luồng nghiệp vụ đầy đủ |
| **MongoDB** cho catalog | PostgreSQL + `JSONB` | Dữ liệu có quan hệ và ràng buộc; một engine dễ vận hành hơn |
| **Elasticsearch** | Tìm kiếm toàn văn bằng `tsvector` + GIN index của PostgreSQL | Tốn RAM, cần pipeline đồng bộ; với dữ liệu demo `tsvector` đủ dùng. Bước tiếp: đồng bộ sang Elasticsearch bằng chính event `product.*` |
| **Inventory service riêng** | Gộp vào Order (chung `order_db`) | Giữ hàng phải nguyên tử với tạo đơn; tách ra thành giao dịch phân tán |
| **Thông báo SMS** | Chỉ email (Mailpit) | Cần nhà cung cấp trả phí; về kiến trúc giống email |
| **Tốc độ bán một SKU nóng** | Khoảng 30 đơn/giây (các đơn cùng SKU đi tuần tự qua khóa); request chờ quá 3 giây nhận `409 INVENTORY_BUSY` | Chọn tính đúng trước; hướng tăng tốc: chia kho nhiều dòng, xếp hàng đơn qua queue |

**Hạn chế đã biết:**
- Lỗi `503` ở lần gọi Feign đầu tiên sau khi khởi động: **đã có bản sửa và unit test** (làm nóng lúc khởi động + thử lại 1 lần), **chưa kiểm chứng lại dưới tải** ([chi tiết](Flashsale_Results.md#phát-hiện-phụ-lỗi-503-ở-lần-chạy-đầu-sau-khi-khởi-động-đã-có-bản-sửa)); lời gọi gRPC đầu tiên vẫn chậm hơn bình thường.
- Load test chưa chứng minh được 500–1.000 đơn thành công/giây (xem 3.1).
- Web chỉ truy cập được từ Việt Nam.

## 5. Đã bổ sung sau bản demo đầu (nhánh `feature/mo-rong`)

1. `Idempotency-Key` cho `POST /orders`; relay outbox dùng `SKIP LOCKED` để chạy nhiều instance.
2. Outbox phía Product: SKU mới tự có dòng kho ở order-service.
3. VNPAY sandbox qua `PaymentGatewayAdapter` có sẵn (IPN + return URL).
4. Tìm kiếm toàn văn bằng `tsvector` (Elasticsearch để bước sau).
5. Bản sửa lỗi `503` lúc order-service vừa khởi động: `FeignWarmup` + thử lại 1 lần khi lỗi tạm thời (có unit test, chưa kiểm chứng dưới tải).
6. CD qua SSH (`appleboy/ssh-action`) thay cho cron: deploy tag bất biến `sha-<commit>`, tự quay lại bản trước khi health check lỗi, GitHub Environment `production`.

## 6. Hướng phát triển tiếp

- Elasticsearch đồng bộ từ event `product.*` khi dữ liệu lớn.
- Dọn bảng `idempotency_keys` định kỳ (giữ khoảng 24 giờ).
- Hoàn tiền tự động qua API hoàn tiền của VNPAY cho các payment `needs_refund`.
- App mobile dùng chung API với web.
- Truyền trace context qua Outbox (lưu `traceparent` cùng event) để một trace nối liền request → event → consumer.
