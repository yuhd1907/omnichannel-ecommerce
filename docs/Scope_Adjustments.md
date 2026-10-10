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
| 2 | Elasticsearch cho tìm kiếm | Lọc theo danh mục + phân trang trên PostgreSQL | Tốn RAM, cần pipeline đồng bộ chưa có; dữ liệu demo nhỏ | Hoãn |
| 3 | Inventory là service riêng | Gộp vào **Order & Inventory**, chung `order_db` | Giữ hàng phải nguyên tử với tạo đơn; tách ra buộc phải làm giao dịch phân tán | Đã gộp |
| 4 | Hoàn tiền (refund) | Không hoàn tiền tự động; đối soát thủ công | Là một luồng nghiệp vụ riêng phụ thuộc API từng cổng thanh toán | Hoãn |
| 5 | Thông báo SMS | Chỉ email giả lập (kế hoạch) | Cần nhà cung cấp trả phí; về kiến trúc không khác email | Hoãn |

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
- **Cần pipeline đồng bộ dữ liệu.** Elasticsearch là bản sao để đọc; nguồn dữ liệu thật vẫn là `product_db`. Muốn kết quả tìm kiếm không trả về giá cũ hay sản phẩm đã ngừng bán, phải có cơ chế đẩy thay đổi sang (event `ProductSkuCreated`/`ProductUpdated` qua outbox). Cơ chế outbox cho Product hiện **chưa triển khai**, nên một Elasticsearch không được đồng bộ đúng sẽ gây sai lệch nhiều hơn lợi ích.
- **Quy mô dữ liệu chưa cần.** Dữ liệu demo có khoảng chục sản phẩm. `GET /api/v1/products?categoryId=&page=&size=` dùng index `idx_products_category_id` và phân trang của PostgreSQL là đủ.

**Hướng mở rộng:** bước 1 là full-text search ngay trong PostgreSQL (`tsvector` + index GIN), không cần thêm hạ tầng. Bước 2, khi đã có outbox event của Product, mới đưa Elasticsearch vào như một read model tiêu thụ event đó.

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

- **Thứ tự ưu tiên.** Payment service hiện mới là khung. Phải làm xong luồng nền tảng trước: tạo phiên thanh toán, webhook đã xác minh chữ ký, chống webhook lặp (`gateway_txn_id UNIQUE`), phát `PaymentSucceeded`/`PaymentFailed`. Hoàn tiền chỉ có nghĩa khi luồng này đã chạy.
- **Là một luồng nghiệp vụ đầy đủ, không phải một API.** Hoàn tiền cần gọi API hoàn tiền riêng của từng cổng (VNPAY, MoMo) cùng môi trường sandbox tương ứng, mô hình trạng thái riêng (yêu cầu → đang xử lý → thành công/thất bại, hoàn một phần hay toàn phần), bảng `refunds`, quyền admin phê duyệt và đối soát với cổng thanh toán.

**Cách bù trong MVP:** trường hợp rủi ro nhất là *tiền về sau khi đơn đã bị hủy* (job timeout hoặc người dùng hủy chạy trước webhook). Hệ thống **không tự xác nhận lại đơn** vì hàng đã được nhả cho người khác. Thay vào đó, sự kiện được ghi log/cảnh báo để admin đối soát và hoàn tiền thủ công (contract, mục 5). Đơn không bao giờ bị âm thầm đánh dấu `PAID` mà không có hàng.

### 3.5 Hoãn thông báo SMS

**Lý do**

- **Chi phí và thủ tục.** Gửi SMS thật cần nhà cung cấp tính phí theo tin nhắn và thường phải đăng ký tên thương hiệu người gửi, điều không khả thi trong thời gian của đồ án.
- **Không thêm giá trị kiến trúc.** Về mặt hệ thống, SMS và email giống nhau: đều là một consumer nhận `OrderConfirmed`/`OrderCancelled` từ RabbitMQ, gửi đi và ghi log. Làm tốt một kênh đã chứng minh được thiết kế hướng sự kiện.

**Cách bù / mở rộng:** Notification gửi email giả lập và ghi `notification_logs` với `UNIQUE (source_event_id, recipient)` để không gửi lặp khi nhận event trùng; lỗi Notification không rollback Order. Thêm SMS chỉ là thêm một bộ gửi theo kênh dùng chung bảng log; luồng event và các service khác không đổi.

## 4. Các giới hạn MVP khác (đã ghi trong contract)

- **Không đa kho:** không có `location_id` hay phân bổ hàng theo kho; `orders.channel` (`WEB`/`MOBILE`) là kênh client tự khai, server không xác minh được.
- **Không có** khuyến mại, thuế, phí vận chuyển động; `total_amount` = tổng `subtotal` các dòng hàng.
- **`POST /orders` chưa có `Idempotency-Key`:** client retry có thể tạo đơn thứ hai. Cần bổ sung trước khi mở bán thật.

## 5. Phần cốt lõi được giữ nguyên

Các điều chỉnh trên chỉ cắt *thành phần*, không cắt *kiến trúc*. Những phần sau đã triển khai và kiểm chứng chạy được trên Docker Compose:

- Microservices với **database per service** (5 database trên PostgreSQL); các service không đọc database của nhau.
- **API Gateway** là cửa vào duy nhất: xác thực JWT (`JwtAuthGlobalFilter`) và giới hạn tần suất bằng Redis (`RequestRateLimiter`, theo user hoặc IP). Cấu hình prod chỉ mở cổng 8080 ra ngoài.
- **gRPC** cho lời gọi đồng bộ Order → Product (lấy SKU/giá lúc checkout); **REST/Feign** cho Order → Identity (địa chỉ giao hàng).
- Giữ hàng nguyên tử, chống bán vượt kho bằng cập nhật có điều kiện + ràng buộc CHECK; khóa phân tán Redisson theo `sku_code` trước transaction để giảm tranh chấp khi nhiều người mua cùng SKU.
- Phân quyền `USER`/`ADMIN` ở từng service, bên cạnh lớp xác thực ở gateway.

Phần còn đang làm theo kế hoạch: publish outbox lên RabbitMQ (Order đã ghi event vào bảng `outbox_events`, chưa có worker gửi đi), consumer ở Payment/Notification, job hủy đơn quá hạn giữ hàng, Payment và Notification.
