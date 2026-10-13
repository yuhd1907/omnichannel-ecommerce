# Omnichannel E-Commerce

[![CI](https://github.com/yuhd1907/omnichannel-ecommerce/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/yuhd1907/omnichannel-ecommerce/actions/workflows/ci.yml)

Hệ thống thương mại điện tử đa kênh (Web / Mobile) theo kiến trúc **microservices**, viết bằng Java 17 + Spring Boot 4. Mỗi bounded context là một service với database riêng; client chỉ đi qua API Gateway. Các service phối hợp đặt hàng – thanh toán – thông báo bằng **Saga** qua RabbitMQ với **Transactional Outbox**.

**Bản chạy thật:** https://huyecommerce.duckdns.org ([Swagger UI](https://huyecommerce.duckdns.org/swagger-ui.html)). VPS chỉ nhận kết nối từ Việt Nam do nhà cung cấp chặn quốc tế.

![Kiến trúc hệ thống](docs/architecture.png)

## Tài liệu

| Tài liệu | Nội dung |
| --- | --- |
| [docs/Project_Overview.md](docs/Project_Overview.md) | **Tổng quan: đã làm được gì, chưa làm được gì** và hướng phát triển |
| [docs/DDD_DB_API_Contract.md](docs/DDD_DB_API_Contract.md) | Bounded context, schema từng database, API contract v1, luồng checkout và giữ hàng |
| [docs/Messaging_Topology.md](docs/Messaging_Topology.md) | RabbitMQ topology, DLQ, Outbox relay, consumer idempotent, hạn chế đã biết |
| [docs/Scope_Adjustments.md](docs/Scope_Adjustments.md) | Đối chiếu với đề bài và lý do điều chỉnh phạm vi |
| [docs/Flashsale_Results.md](docs/Flashsale_Results.md) | Thử nghiệm flash sale bằng k6 (100 người / 10 sản phẩm): cách chạy và kết quả |
| [docs/Demo_Script.md](docs/Demo_Script.md) | Kịch bản demo trực tiếp từng bước + câu hỏi thường gặp |
| [docs/deployment-notes.md](docs/deployment-notes.md) | Vận hành: deploy tự động, Nginx/HTTPS, giám sát, xử lý outbox `FAILED` và DLQ |
| [docs/erd/](docs/erd/) | ERD của từng database |

## Công nghệ

| Thành phần | Công nghệ |
| --- | --- |
| Ngôn ngữ / framework | Java 17, Spring Boot 4.1.1, Gradle 9.7.1 (multi-module) |
| API Gateway | Spring Cloud Gateway (WebFlux) 2025.1.3, rate limit bằng Redis, Swagger UI gom 4 service |
| Giao tiếp đồng bộ | REST (JSON), OpenFeign, **gRPC** (Protobuf) |
| Giao tiếp bất đồng bộ | RabbitMQ 3.13, Transactional Outbox (publisher confirm), consumer idempotent, DLQ |
| Database | PostgreSQL 16, Flyway migration — database per service |
| Cache / lock | Redis 7, Redisson (khóa phân tán theo `sku_code` khi giữ hàng) |
| Bảo mật | JWT (HS512, jjwt 0.13), RBAC `USER` / `ADMIN`, webhook ký HMAC-SHA256 |
| Giám sát | Micrometer + Prometheus + Grafana (dashboard có sẵn) |
| Email | SMTP tới Mailpit (hộp thư giả lập) |
| Đóng gói / CI-CD | Docker multi-stage, Docker Compose, GitHub Actions → GHCR, VPS tự kéo image |
| Kiểm thử | JUnit 5 + Mockito, Postman smoke test, k6 load test |

## Các service

| Service | Cổng | Database | Chức năng |
| --- | --- | --- | --- |
| `api-gateway` | 8080 | — | Định tuyến, xác thực JWT, rate limit, Swagger UI |
| `identity-service` | 8081 | `identity_db` | Đăng ký, đăng nhập, refresh token, địa chỉ giao hàng |
| `product-service` | 8082 (REST), 9082 (gRPC) | `product_db` | Catalog, quản trị sản phẩm, gRPC `GetSku` / `BatchGetSkus` |
| `order-service` | 8083 | `order_db` | Đặt / hủy đơn, giữ hàng nguyên tử, vòng đời giao hàng, quản trị tồn kho, hủy đơn quá hạn |
| `payment-service` | 8084 | `payment_db` | Phiên thanh toán (MOCK), webhook HMAC, chống webhook lặp |
| `notification-service` | 8085 | `notification_db` | Nhận event đơn hàng, lưu thông báo, gửi email |

Module dùng chung: `common` (envelope response, mã lỗi, JWT filter) và `grpc-api` (file `.proto` + stub sinh tự động).

**Luồng chính:** tạo đơn (`PENDING`, giữ hàng) → thanh toán → webhook `SUCCESS` → `CONFIRMED / PAID` → admin giao hàng `SHIPPING` → `COMPLETED`. Thanh toán thất bại hoặc quá 15 phút → `CANCELLED`, trả kho. Mỗi bước phát event và gửi email.

## Chạy toàn hệ thống bằng 1 lệnh

**Yêu cầu:** Docker Desktop (Compose ≥ v2.24) và **còn trống ≥ 6 GB RAM**. Không cần cài JDK vì code được build trong container.

```powershell
docker compose up -d --build --wait
```

Lệnh này build 6 image, khởi động 12 container và chỉ trả về khi tất cả đã sẵn sàng. Lần build đầu mất khoảng 5–10 phút để tải dependency. File `.env` **không bắt buộc**; muốn tùy chỉnh thì `Copy-Item .env.example .env`.

| Địa chỉ | Nội dung |
| --- | --- |
| http://localhost:8080/swagger-ui.html | Tài liệu API (chọn service ở góc phải; nút **Authorize** dán access token) |
| http://localhost:8025 | Mailpit — hộp thư nhận email của hệ thống |
| http://localhost:3000 | Grafana (`admin` / `admin`) — dashboard "Omnichannel - Tổng quan" |
| http://localhost:9090 | Prometheus |
| http://localhost:15672 | RabbitMQ Management (`guest` / `guest`) |

Kiểm tra nhanh:

```powershell
docker compose ps
curl.exe -s http://localhost:8080/actuator/health                               # {"status":"UP",...}
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8080/api/v1/products     # 200 (public)
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8080/api/v1/orders       # 401 (cần JWT)
```

Lệnh thường dùng:

```powershell
docker compose logs -f order-service     # xem log một service
docker compose up -d --build api-gateway # build lại một service sau khi sửa code
docker compose down                      # dừng, giữ dữ liệu
docker compose down -v                   # dừng và XÓA toàn bộ dữ liệu (volume)
```

## Kiểm thử

| Loại | Cách chạy |
| --- | --- |
| Unit test | `$env:CI="true"; .\gradlew clean build` (`CI=true` bỏ qua test `@Tag("integration")` cần DB thật) |
| Smoke test end-to-end | Import [postman/Omnichannel-Smoke.postman_collection.json](postman/Omnichannel-Smoke.postman_collection.json), tạo environment có `gatewayUrl` = `http://localhost:8080/api/v1` và `webhookSecret` = `dev-webhook-secret-key-12345`, chạy lần lượt folder 1 → 5 (S1 thanh toán thành công, S2 thất bại trả kho, S3 admin giao hàng) |
| Dấu vết Saga một đơn | `Get-Content scripts\saga-check.sql \| docker exec -i ecommerce-postgres psql -U postgres -d order_db -v order_id=<UUID>` |
| Tính đúng khi tranh mua | [scripts/flashsale-k6.js](scripts/flashsale-k6.js) — 100 người / kho 10, kết quả ở [Flashsale_Results.md](docs/Flashsale_Results.md) |
| Thông lượng | [scripts/load-order-k6.js](scripts/load-order-k6.js) — 100 → 1000 req/s qua gateway, kiểm bảo toàn kho bằng `scripts/load-verify.sql` (hướng dẫn ở đầu file) |

## Triển khai (prod)

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --wait
```

[docker-compose.prod.yml](docker-compose.prod.yml) dùng image build sẵn trên GHCR và chỉ ghi đè phần cổng:

- `api-gateway` bind `127.0.0.1:8080`; **Nginx là reverse proxy duy nhất** nhận HTTPS từ Internet ([deploy/nginx/omnichannel.conf](deploy/nginx/omnichannel.conf)).
- 5 service ứng dụng không publish cổng nào; chúng gọi nhau qua mạng Docker bằng tên service.
- PostgreSQL, Redis, RabbitMQ, Mailpit, Prometheus, Grafana chỉ bind `127.0.0.1`, xem qua SSH tunnel.

Lưu ý: `ufw` **không** chặn được cổng do Docker publish, vì Docker tự ghi luật iptables vượt qua ufw. Phải chặn ngay trong file compose như trên.

**CD:** merge vào `main` → GitHub Actions chạy test, build 6 image và push GHCR → cron trên VPS chạy [scripts/auto-deploy.sh](scripts/auto-deploy.sh) mỗi 2 phút, thấy code hoặc image mới thì `up -d`. Lý do không dùng SSH-push: [Scope_Adjustments.md](docs/Scope_Adjustments.md) mục 6.

## Chạy từng service khi phát triển

Chạy hạ tầng bằng Docker, chạy service cần sửa bằng IDE hoặc Gradle. Cần JDK 17.

```powershell
Copy-Item .env.example .env              # các *_HOST trong .env đã là localhost
docker compose up -d --wait postgres redis rabbitmq mailpit
.\gradlew.bat :order-service:bootRun     # bootRun tự nạp biến từ .env
```

## Hạ tầng và thông tin kết nối

| Thành phần | Địa chỉ (từ máy host) | Tài khoản |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | user `postgres`, mật khẩu `POSTGRES_PASSWORD` trong `.env` (không có `.env` thì `123456!`) |
| Redis | `localhost:6379` | không mật khẩu |
| RabbitMQ | `localhost:5672`, UI `localhost:15672` | `RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS` (mặc định `guest` / `guest`) |

```powershell
docker exec -it ecommerce-postgres psql -U postgres -d order_db
```

Năm database được tạo bởi [docker/postgres/init-databases.sql](docker/postgres/init-databases.sql). Script này **chỉ chạy khi volume còn trống**; schema từng database do Flyway quản lý trong `<service>/src/main/resources/db/migration`.

## Cấu trúc thư mục

```
.
├── api-gateway/              # Spring Cloud Gateway: route, JWT filter, rate limit, Swagger UI
├── identity-service/
├── product-service/          # REST + gRPC server
├── order-service/            # Order & Inventory (chung order_db), outbox relay
├── payment-service/          # thanh toán MOCK, webhook, outbox relay
├── notification-service/     # consumer RabbitMQ, gửi email
├── common/                   # ApiResponse, ErrorCode, JWT filter dùng chung
├── grpc-api/                 # product_internal.proto → stub Java
├── deploy/
│   ├── nginx/                # cấu hình Nginx + HTTPS trên VPS
│   └── monitoring/           # Prometheus scrape + Grafana datasource/dashboard
├── docker/postgres/          # script tạo 5 database
├── scripts/                  # k6 load test, SQL kiểm tra, auto-deploy.sh
├── postman/                  # smoke test end-to-end
├── docs/                     # thiết kế, sơ đồ, kịch bản demo
├── docker-compose.yml        # local: mở cổng mọi service để debug
├── docker-compose.prod.yml   # prod: image GHCR, chỉ gateway cho Nginx
└── .env.example
```
