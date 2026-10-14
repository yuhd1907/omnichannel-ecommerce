# Đối chiếu với đề bài và lý do điều chỉnh phạm vi

Tài liệu này liệt kê những điểm hệ thống **khác** với đề bài, lý do của từng quyết định, cách hệ thống bù lại phần bị cắt và hướng mở rộng nếu có thêm thời gian. Thiết kế chi tiết (schema, API, luồng checkout) nằm ở [DDD_DB_API_Contract.md](DDD_DB_API_Contract.md); sơ đồ tổng thể ở [architecture.png](architecture.png).

## 1. Bối cảnh ra quyết định

Ba ràng buộc chi phối mọi điều chỉnh bên dưới:

- **Thời gian:** hạn nộp 15/10/2026. Ưu tiên một luồng nghiệp vụ chạy đúng từ đầu đến cuối (đăng nhập → xem sản phẩm → đặt hàng → giữ hàng) hơn là nhiều thành phần chạy dở.
- **Tài nguyên triển khai:** toàn hệ thống chạy trên một máy bằng Docker Compose. Sáu ứng dụng Spring Boot được giới hạn 512 MB mỗi container (~3 GB), hạ tầng (PostgreSQL, Redis, RabbitMQ) thêm ~1 GB. Mỗi thành phần hạ tầng mới phải đủ giá trị để chiếm phần RAM còn lại.
- **Tính đúng đắn trước, mở rộng sau:** chỗ nào có rủi ro sai dữ liệu (bán vượt kho, trừ tiền hai lần) thì chọn giải pháp mà database tự đảm bảo được, thay vì phối hợp qua mạng.

## 2. Bảng đối chiếu

| # | Đề bài | Hệ thống hiện tại | Lý do chính | Trạng thái |
| --- | --- | --- | --- | --- |
| 1 | MongoDB cho Product Catalog | PostgreSQL 16, thuộc tính biến thể lưu `JSONB` | Dữ liệu catalog có quan hệ và ràng buộc chặt; `JSONB` đã đáp ứng phần schema linh hoạt | Đã thay thế |
| 2 | Elasticsearch cho tìm kiếm | Tìm kiếm toàn văn bằng `tsvector` + GIN index trên PostgreSQL (không dấu, khớp tiền tố, sắp theo độ liên quan) | Tốn RAM, dữ liệu demo nhỏ; event `product.*` đã có để đồng bộ khi cần | Thay bằng `tsvector`, Elasticsearch hoãn |
| 3 | Inventory là service riêng | Gộp vào **Order & Inventory**, chung `order_db` | Giữ hàng phải nguyên tử với tạo đơn; tách ra buộc phải làm giao dịch phân tán | Đã gộp |
| 4 | Hoàn tiền (refund) | Không hoàn tiền tự động; đối soát thủ công | Là một luồng nghiệp vụ riêng phụ thuộc API từng cổng thanh toán | Hoãn |
| 5 | Thông báo SMS | Chỉ email, gửi qua SMTP tới Mailpit (hộp thư giả lập, xem tại cổng 8025) | Cần nhà cung cấp trả phí; về kiến trúc không khác email | Hoãn |
| 6 | CD qua SSH (`appleboy/ssh-action`) | CD dạng pull thay cho `appleboy/ssh-action` | Nhà cung cấp VPS chặn kết nối quốc tế vào cổng 22 (GitHub Actions báo `dial tcp :22 i/o timeout`) và chặn VPS kết nối ra hạ tầng Azure của GitHub Actions (`pipelines/broker.actions.githubusercontent.com` timeout), nên không dùng được SSH-push lẫn self-hosted runner. Giải pháp: GitHub Actions build và push image lên GHCR; trên VPS, cron chạy `scripts/deploy/auto-deploy.sh` mỗi 2 phút (`git fetch` + `docker compose pull`, có thay đổi thì `up -d --remove-orphans`). Mô hình giống GitOps (Argo CD, Flux). Độ trễ deploy tối đa khoảng 2 phút. | Đã thay thế |
| 7 | Chứng chỉ SSL bằng Certbot | Let's Encrypt cấp bằng `certbot --nginx` (HTTP-01), tự gia hạn qua `certbot.timer` | Ban đầu nhà cung cấp VPS chặn kết nối quốc tế vào cổng 80 nên HTTP-01 timeout, phải cấp tạm qua DNS-01 (bản ghi TXT DuckDNS, gia hạn tay). Sau khi nhà cung cấp mở kết nối quốc tế (12/10/2026), cấp lại bằng HTTP-01 đúng như đề tài; `certbot renew --dry-run` thành công | Đúng đề tài |
| 8 | Tích hợp cổng thanh toán thật (VNPAY/MoMo, QR) | `MockAdapter` (webhook ký HMAC-SHA256) và **`VnpayAdapter` cho VNPAY sandbox** (URL thanh toán ký HMAC-SHA512, IPN + return URL) cùng sau interface `PaymentGatewayAdapter`; cả hai đi chung `WebhookService.applyResult` (chống lặp bằng `gateway_txn_id UNIQUE`, so khớp số tiền) | Môi trường thật cần hợp đồng merchant; sandbox chỉ cần đăng ký miễn phí. Máy chủ VNPAY ở Việt Nam nên IPN vào được VPS. Chạy local (VNPAY không gọi được localhost) thì return URL cũng áp kết quả sau khi kiểm chữ ký. MoMo / QR chưa làm. | Sandbox VNPAY |
| 9 | Monitoring & Observability (Prometheus, Loki + Promtail, Jaeger/Zipkin + OpenTelemetry) | **Đủ ba phần:** Prometheus + Grafana (metrics, dashboard có sẵn); **Loki 3.5 + Promtail 3.5** gom log mọi container qua `docker.sock`; **OpenTelemetry → Jaeger** (`spring-boot-starter-opentelemetry`, OTLP HTTP) trace qua gateway, REST, Feign, gRPC, RabbitMQ; `traceId` có trong mọi response và mọi dòng log, Grafana bấm từ log sang trace và ngược lại. Tất cả chỉ bind `127.0.0.1`, xem qua SSH tunnel; Nginx chặn `/actuator/*` trừ `/actuator/health` | Metrics/log/trace lộ cấu trúc hệ thống nên không mở ra Internet. Promtail đã hết hỗ trợ (EOL 03/2026, thay bằng Grafana Alloy) nhưng đề tài yêu cầu Promtail nên dùng bản cuối 3.5; đổi sang Alloy chỉ cần thay 1 file cấu hình + image. Trace của luồng bất đồng bộ qua Outbox tách thành trace riêng (relay chạy theo lịch, không mang context của request gốc) | Đã triển khai |
| 10 | Client Web / Mobile App | Web **Next.js 16** (thư mục `frontend/`): cửa hàng, tìm kiếm, giỏ hàng, đặt hàng có `Idempotency-Key`, thanh toán MOCK/VNPAY, theo dõi Saga, trang quản trị đơn và tồn kho. Chạy trong container, Nginx phục vụ ở `/`, CI build + push image `omnichannel-frontend` | Đề tài chỉ yêu cầu backend; web giúp demo trực quan. Chưa có app mobile (cùng API `channel=MOBILE` đã hỗ trợ) | Bổ sung |

## 3. Chi tiết từng điều chỉnh

### 3.1 Bỏ MongoDB — Product Catalog dùng PostgreSQL + JSONB

**Lý do**

- **Dữ liệu catalog mang tính quan hệ.** Danh mục là cây (`categories.parent_id`), sản phẩm thuộc danh mục và thương hiệu, mỗi SKU thuộc một sản phẩm và `sku_code` phải duy nhất toàn hệ thống vì Order và Inventory tham chiếu theo nó. Migration hiện tại dùng FK, `UNIQUE (sku_code)`, `CHECK (price >= 0)` và `CHECK (status IN (...))` để database tự từ chối dữ liệu sai. Với MongoDB, các ràng buộc này phải tự viết ở tầng ứng dụng.
- **Phần "schema linh hoạt" đã có lời giải.** Lý do thường gặp để chọn MongoDB cho catalog là mỗi ngành hàng có thuộc tính khác nhau (áo: màu/size; laptop: RAM/CPU). Cột `product_skus.attributes JSONB` lưu được cấu trúc tùy ý cho từng SKU mà không cần đổi schema, và vẫn truy vấn/đánh index được khi cần.
- **Vận hành đơn giản hơn.** Cả năm database dùng chung một engine: một container, một công cụ migration (Flyway), một cách sao lưu, một bộ kỹ năng cho cả nhóm. Không phải thêm một container MongoDB vào ngân sách RAM.

**Không mất gì về kiến trúc:** Order không đọc `product_db`; nó chỉ lấy SKU/giá qua gRPC (`ProductInternalService.BatchGetSkus`). Nếu sau này đổi engine của Product Catalog, chỉ product-service thay đổi, hợp đồng Protobuf giữ nguyên.

### 3.2 Hoãn Elasticsearch

**Lý do**

- **Chi phí tài nguyên.** Elasticsearch tự chạy trên JVM và cần heap riêng cỡ GB để hoạt động ổn định, tức là xấp xỉ gấp đôi một service ứng dụng. Thêm vào sẽ vượt ngân sách RAM đã tính ở mục 1.
- **Cần pipeline đồng bộ dữ liệu.** Elasticsearch là bản sao để đọc; nguồn dữ liệu thật vẫn là `product_db`. Muốn kết quả tìm kiếm không trả về giá cũ hay sản phẩm đã ngừng bán, phải đẩy mọi thay đổi sang qua outbox. Product đã có outbox nhưng mới phát `product.sku.created`; cần thêm `product.updated` / `product.deactivated` trước khi dựng read model.
- **Quy mô dữ liệu chưa cần.** Dữ liệu demo có khoảng chục sản phẩm. `GET /api/v1/products?categoryId=&page=&size=` dùng index `idx_products_category_id` và phân trang của PostgreSQL là đủ.

**Đã làm bước 1:** full-text search ngay trong PostgreSQL (`V5__product_full_text_search.sql`): cột `search_vector` generated từ tên (trọng số A) + mô tả (C) đã bỏ dấu, GIN index, `GET /products?q=`. Không thêm hạ tầng, tự cập nhật khi sửa sản phẩm. **Bước 2:** đưa Elasticsearch vào như một read model tiêu thụ event `product.*`.

### 3.3 Gộp Inventory vào Order — không có Inventory service riêng

**Lý do**

- **Giữ hàng phải nguyên tử với tạo đơn.** Khi đặt hàng, hệ thống phải *đồng thời* tạo đơn `PENDING` và trừ tồn kho; không được có trạng thái "đơn đã tạo nhưng chưa giữ hàng" hoặc ngược lại. Nếu Inventory là service riêng, đây trở thành một **giao dịch phân tán** và kéo theo:
  - saga cùng các bước bù trừ (compensation) khi một bên thành công một bên thất bại;
  - xử lý timeout mơ hồ: gọi reserve bị timeout thì kho *đã trừ hay chưa*?
  - idempotency key cho API reserve để retry không trừ kho hai lần;
  - thêm một chặng gọi mạng ngay trên đường checkout, đúng chỗ tải cao nhất khi flash sale.
- **Gộp lại thì PostgreSQL tự bảo đảm tính đúng.** Trong một transaction của `order_db`: ghi `orders` + `order_items`, rồi với mỗi SKU chạy cập nhật có điều kiện `... SET available_qty = available_qty - :q, reserved_qty = reserved_qty + :q WHERE sku_code = :sku AND available_qty >= :q` (xem `InventoryRepository`). Cập nhật nào không khớp dòng nào thì rollback cả đơn. Thêm `CHECK (available_qty >= 0 AND reserved_qty >= 0)` làm chốt chặn cuối: không thể bán vượt kho kể cả khi có lỗi ở tầng ứng dụng.
- **Inventory của MVP rất mỏng.** MVP không đa kho: Inventory chỉ là một bảng, mỗi `sku_code` một dòng. Một service riêng cho một bảng sẽ tốn thêm một JVM (512 MB), một database và một đường mạng, trong khi không đem lại lợi ích nào về khả năng mở rộng ở quy mô hiện tại.

**Vẫn giữ ranh giới để tách được sau này**

- Inventory có entity, repository, service (`InventoryService`) và controller quản trị riêng (`/api/v1/admin/inventory/**`). Bảng `inventory` không có FK tới bảng nào của Order.
- `inventory.sku_code` là **tham chiếu logic**, không có FK sang `product_db`.
- Mọi thay đổi tồn kho đi qua các thao tác có tên rõ ràng (`reserve`, `release`, điều chỉnh admin), không có chỗ nào cập nhật số lượng tùy ý.

**Việc cần làm trước khi tách:** hiện `OrderServiceImpl` gọi thẳng `InventoryRepository.reserve/release`; `InventoryService` mới phục vụ API admin. Bước đầu tiên khi tách là chuyển `reserve`/`release` vào `InventoryService` để Order chỉ phụ thuộc interface. Sau đó thay cài đặt nội bộ bằng một client gọi API/event và áp dụng saga cho bước reserve. Dữ liệu và hợp đồng API của Order không đổi.

### 3.4 Hoãn hoàn tiền (refund)

**Lý do**

- **Thứ tự ưu tiên.** Ưu tiên làm xong luồng nền tảng trước (đã xong): tạo phiên thanh toán, webhook đã xác minh chữ ký, chống webhook lặp (`gateway_txn_id UNIQUE`), phát `PaymentSucceeded`/`PaymentFailed` qua outbox. Hoàn tiền chỉ có nghĩa khi luồng này đã chạy.
- **Là một luồng nghiệp vụ đầy đủ, không phải một API.** Hoàn tiền cần gọi API hoàn tiền riêng của từng cổng (VNPAY, MoMo) cùng môi trường sandbox tương ứng, mô hình trạng thái riêng (yêu cầu → đang xử lý → thành công/thất bại, hoàn một phần hay toàn phần), bảng `refunds`, quyền admin phê duyệt và đối soát với cổng thanh toán.

**Cách bù trong MVP:** trường hợp rủi ro nhất là *tiền về sau khi đơn đã bị hủy* (job timeout hoặc người dùng hủy chạy trước webhook). Hệ thống **không tự xác nhận lại đơn** vì hàng đã được nhả cho người khác. Thay vào đó, sự kiện được ghi log/cảnh báo để admin đối soát và hoàn tiền thủ công (contract, mục 5). Đơn không bao giờ bị âm thầm đánh dấu `PAID` mà không có hàng.

### 3.5 Hoãn thông báo SMS

**Lý do**

- **Chi phí và thủ tục.** Gửi SMS thật cần nhà cung cấp tính phí theo tin nhắn và thường phải đăng ký tên thương hiệu người gửi, điều không khả thi trong thời gian của đồ án.
- **Không thêm giá trị kiến trúc.** Về mặt hệ thống, SMS và email giống nhau: đều là một consumer nhận `OrderConfirmed`/`OrderCancelled` từ RabbitMQ, gửi đi và ghi log. Làm tốt một kênh đã chứng minh được thiết kế hướng sự kiện.

**Cách bù / mở rộng:** Notification nhận cả 5 event của đơn (`order.created/confirmed/cancelled/shipped/completed`), ghi `processed_messages` (khóa chính là `messageId` = id outbox) cùng transaction với `notifications` để không xử lý lặp khi nhận event trùng, rồi gửi email thật qua SMTP **sau khi commit** tới Mailpit. Email người nhận là snapshot lấy từ JWT lúc đặt đơn (`orders.customer_email`), đi kèm mọi event. Gửi mail là best-effort: SMTP lỗi chỉ ghi log, không làm retry message hay rollback Order. Thêm SMS chỉ là thêm một bộ gửi theo kênh; luồng event và các service khác không đổi.

## 4. Các giới hạn MVP khác (đã ghi trong contract)

- **Không đa kho:** không có `location_id` hay phân bổ hàng theo kho; `orders.channel` (`WEB`/`MOBILE`) là kênh client tự khai, server không xác minh được.
- **Không có** khuyến mại, thuế, phí vận chuyển động; `total_amount` = tổng `subtotal` các dòng hàng.
- **`Idempotency-Key` là tùy chọn:** client không gửi header thì retry vẫn có thể tạo đơn thứ hai (giữ tương thích với k6 load test; web luôn gửi key). Bảng `idempotency_keys` chưa có job dọn định kỳ.

## 5. Phần cốt lõi được giữ nguyên

Các điều chỉnh trên chỉ cắt *thành phần*, không cắt *kiến trúc*. Những phần sau đã triển khai và kiểm chứng chạy được trên Docker Compose:

- Microservices với **database per service** (5 database trên PostgreSQL); các service không đọc database của nhau.
- **API Gateway** là cửa vào duy nhất: xác thực JWT (`JwtAuthGlobalFilter`) và giới hạn tần suất bằng Redis (`RequestRateLimiter`, theo user hoặc IP). Cấu hình prod chỉ mở cổng 8080 ra ngoài.
- **gRPC** cho lời gọi đồng bộ Order → Product (lấy SKU/giá lúc checkout); **REST/Feign** cho Order → Identity (địa chỉ giao hàng).
- Giữ hàng nguyên tử, chống bán vượt kho bằng cập nhật có điều kiện + ràng buộc CHECK; khóa phân tán Redisson theo `sku_code` trước transaction để giảm tranh chấp khi nhiều người mua cùng SKU.
- Phân quyền `USER`/`ADMIN` ở từng service, bên cạnh lớp xác thực ở gateway.

- **Saga điều phối bằng sự kiện** qua RabbitMQ với **Transactional Outbox** ở Order và Payment (relay có publisher confirm + `mandatory`), consumer idempotent, DLQ cho message hỏng. Luồng đã kiểm chứng trên VPS: thanh toán thành công → `CONFIRMED/PAID`, kho chuyển giữ → bán; thanh toán thất bại hoặc quá hạn 15 phút → `CANCELLED`, nhả kho. Xem dấu vết một đơn trên cả 3 database bằng `scripts/sql/saga-check.sql`.
- **Vòng đời giao hàng** cho admin: `CONFIRMED → SHIPPING → COMPLETED` (`PATCH /api/v1/admin/orders/{id}/status`), chuyển trạng thái có điều kiện, mỗi bước phát event và gửi email.
- **Tài liệu API:** spec OpenAPI 3.1 sinh từ code bằng springdoc, xuất ra `docs/api/*.openapi.json` (`scripts/export-openapi.sh`). Không public Swagger UI trên prod; client demo là web.
- **Kiểm thử tải:** `scripts/load-test/flashsale-k6.js` (tính đúng: 100 người / kho 10, kết quả ở [Flashsale_Results.md](Flashsale_Results.md)) và `scripts/load-test/load-order-k6.js` (thông lượng qua gateway, tăng tới 1000 req/s, kiểm bảo toàn kho bằng `scripts/sql/load-verify.sql`).
- **CI/CD:** GitHub Actions chạy test, build và push image lên GHCR; VPS tự kéo image mới (mục #6).
