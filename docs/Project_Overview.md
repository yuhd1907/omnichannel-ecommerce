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
| Service | 6 Spring Boot service (gateway + 5 nghiệp vụ) + 2 module dùng chung |
| Database | 5 PostgreSQL (database per service), schema quản lý bằng Flyway |
| API | Hơn 30 endpoint REST, 2 RPC gRPC, tài liệu Swagger gom tại gateway |
| Event | 8 loại event qua RabbitMQ (5 `order.*`, 2 `payment.*`, 1 `product.*`), mỗi queue có DLQ |
| Kiểm thử | 145 unit test chạy trong CI + 39 integration test; Postman smoke 3 kịch bản; 2 kịch bản k6 |
| Load test | Giữ **1000 req/s** đặt hàng qua gateway (trung vị 10 ms); 66.784 request, kho 1000 → đúng 1000 đơn, không bán vượt |
| Triển khai | VPS thật, HTTPS, CI/CD tự động: https://huyecommerce.duckdns.org |

## 3. Đã làm được

### 3.1 Nghiệp vụ

| Chức năng | Service | Xem trong demo |
| --- | --- | --- |
| Đăng ký, đăng nhập, refresh token, phân quyền `USER` / `ADMIN` | identity | Postman folder 1–2 |
| Địa chỉ giao hàng | identity | Postman SM08 |
| Danh mục, sản phẩm, SKU biến thể (JSONB), admin CRUD | product | Swagger |
| Tìm kiếm toàn văn (`tsvector`, không dấu, gõ dở vẫn khớp) | product | Demo 2b |
| Đặt hàng, giữ hàng, xem / hủy đơn; `Idempotency-Key` chống tạo đơn trùng khi gửi lại | order | S1, demo 2b |
| Thanh toán MOCK (webhook ký HMAC) và **VNPAY sandbox** (IPN ký HMAC-SHA512) | payment | S1, S2, demo 2b |
| Tự hủy đơn không thanh toán sau 15 phút, trả kho | order | (nói, không demo) |
| Vòng đời giao hàng `CONFIRMED → SHIPPING → COMPLETED` cho admin | order | S3 |
| Quản trị tồn kho (nhập / điều chỉnh); SKU mới tự có dòng kho qua event `product.sku.created` | order | SM05 |
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
  - Giai đoạn hết hàng giữ 1000 req/s với trung vị 10 ms.
  - Giai đoạn còn hàng bán khoảng 30 đơn/giây cho một SKU nóng, vì các đơn phải đi tuần tự qua khóa. Đây là đánh đổi chọn tính đúng ([chi tiết](Flashsale_Results.md#load-test-thông-lượng-qua-gateway-13102026)).

### 3.4 Bảo mật

- JWT HS512. Gateway xác thực token; từng service kiểm tra lại token và phân quyền `USER` / `ADMIN` (không tin gateway tuyệt đối).
- User không xem được đơn của người khác: trả 404 thay vì 403 để không lộ đơn có tồn tại.
- Webhook MOCK ký **HMAC-SHA256**, VNPAY ký **HMAC-SHA512**; đều kiểm tra khớp số tiền và chống xử lý lặp bằng `gateway_txn_id UNIQUE`.
- Trên VPS chỉ mở cổng 22 / 80 / 443 (đã quét nmap từ ngoài). DB, Redis, RabbitMQ, Grafana chỉ bind `127.0.0.1`. Nginx chặn `/actuator/*` trừ health.

### 3.5 Vận hành và DevOps

- Docker multi-stage, chạy toàn hệ thống bằng 1 lệnh `docker compose up`.
- **CI/CD:** GitHub Actions chạy test → build 6 image → push GHCR → VPS tự kéo và deploy trong khoảng 2 phút.
- **HTTPS** Let's Encrypt trên tên miền thật.
- **Giám sát:** Prometheus + Grafana (request/giây, độ trễ p95, mã lỗi, heap JVM, connection DB, queue RabbitMQ).
- **Tài liệu:** thiết kế DDD + API contract, ERD 5 database, sơ đồ kiến trúc và RabbitMQ, hướng dẫn vận hành.

## 4. Chưa làm được / làm khác đề bài

| Hạng mục | Hiện trạng | Lý do chính |
| --- | --- | --- |
| **Giao diện Web / Mobile** | Chưa có. Hệ thống cung cấp API; demo bằng Swagger và Postman | Tập trung thời gian vào backend phân tán, là trọng tâm của đề tài |
| **Cổng thanh toán thật** | VNPAY **sandbox** qua `VnpayAdapter` (cần đăng ký TMN code); chưa có MoMo / QR | Môi trường thật cần hợp đồng merchant |
| **Hoàn tiền tự động** | Đánh dấu `needs_refund`, đối soát thủ công | Mỗi cổng có API hoàn tiền riêng, là một luồng nghiệp vụ đầy đủ |
| **MongoDB** cho catalog | PostgreSQL + `JSONB` | Dữ liệu có quan hệ và ràng buộc; một engine dễ vận hành hơn |
| **Elasticsearch** | Tìm kiếm toàn văn bằng `tsvector` + GIN index của PostgreSQL | Tốn RAM, cần pipeline đồng bộ; với dữ liệu demo `tsvector` đủ dùng. Bước tiếp: đồng bộ sang Elasticsearch bằng chính event `product.*` |
| **Inventory service riêng** | Gộp vào Order (chung `order_db`) | Giữ hàng phải nguyên tử với tạo đơn; tách ra thành giao dịch phân tán |
| **Thông báo SMS** | Chỉ email (Mailpit) | Cần nhà cung cấp trả phí; về kiến trúc giống email |
| **Tốc độ bán một SKU nóng** | Khoảng 30 đơn/giây (các đơn cùng SKU đi tuần tự qua khóa); request chờ quá 3 giây nhận `409 INVENTORY_BUSY` | Chọn tính đúng trước; hướng tăng tốc: chia kho nhiều dòng, xếp hàng đơn qua queue |
| **CD qua SSH, SSL tự gia hạn** | CD kiểu pull (cron 2 phút); SSL cấp qua DNS-01, gia hạn tay 90 ngày | Nhà cung cấp VPS chặn kết nối quốc tế vào cổng 22 và 80 (đã xác minh bằng tcpdump) |

**Hạn chế đã biết:**
- Lần gọi đầu tiên sau khi service khởi động có thể trả 503 (khởi tạo gRPC/Feign chậm), gửi lại là được.
- Web chỉ truy cập được từ Việt Nam.

## 5. Đã bổ sung sau bản demo đầu (nhánh `feature/mo-rong`)

1. `Idempotency-Key` cho `POST /orders`; relay outbox dùng `SKIP LOCKED` để chạy nhiều instance.
2. Outbox phía Product: SKU mới tự có dòng kho ở order-service.
3. VNPAY sandbox qua `PaymentGatewayAdapter` có sẵn (IPN + return URL).
4. Tìm kiếm toàn văn bằng `tsvector` (Elasticsearch để bước sau).

## 6. Hướng phát triển tiếp

- Elasticsearch đồng bộ từ event `product.*` khi dữ liệu lớn.
- Dọn bảng `idempotency_keys` định kỳ (giữ khoảng 24 giờ).
- Hoàn tiền tự động qua API hoàn tiền của VNPAY cho các payment `needs_refund`.
- Giao diện web / app mobile dùng chung API.
