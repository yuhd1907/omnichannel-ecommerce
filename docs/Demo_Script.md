# Kịch bản demo trực tiếp

Thời lượng mục tiêu: khoảng 15 phút demo, phần còn lại để hỏi đáp. Demo trên VPS `https://huyecommerce.duckdns.org`; phần load test chạy trên máy local vì VPS nhỏ.

Ký hiệu: **[VPS]** là lệnh bash chạy trên VPS (prompt `huy@azvps-...`), **[PS]** là PowerShell trên máy mình.

## Trình tự buổi demo

| # | Phần | Thời gian | Mở gì |
| --- | --- | --- | --- |
| 1 | Tổng quan: đã làm / chưa làm, kiến trúc | 3–4 phút | [Project_Overview.md](Project_Overview.md), `architecture.png` |
| 2 | Giao diện web + tài liệu API | 1 phút | Web, `docs/api/*.openapi.json` |
| 2b | Phần mở rộng: tìm kiếm, `Idempotency-Key`, VNPAY sandbox | 3 phút | Web, terminal (`smoke-test.mjs`) |
| 3 | S1 — đặt hàng, thanh toán thành công | 3 phút | Web (trang đơn hàng có theo dõi Saga), Mailpit, `saga-check.sql` |
| 4 | S2 — thanh toán thất bại, trả kho | 2 phút | Web (đơn + trang Quản trị → Tồn kho), Mailpit |
| 5 | S3 — admin giao hàng | 2 phút | Web (trang Quản trị), Mailpit |
| 6 | Bảo mật | 2 phút | Terminal VPS |
| 7 | Chịu lỗi: RabbitMQ chết vẫn nhận đơn | 2 phút | Terminal VPS, Web |
| 7b | Chịu lỗi: tắt Notification vẫn đặt hàng | 1 phút | Terminal VPS, Web, RabbitMQ UI, Mailpit |
| 8 | Giám sát (metrics, log, trace) + load test | 3 phút | Grafana (Prometheus, Loki), Jaeger, kết quả k6 |
| 9 | CI/CD | 1 phút | GitHub Actions, log cron |
| | Hỏi đáp | | Bảng câu hỏi cuối file |

---

## 0. Chuẩn bị (làm trước giờ demo ít nhất 30 phút)

### 0.1 Khóa deploy tự động

Merge vào `main` trong lúc demo sẽ làm hệ thống khởi động lại 1–3 phút. Tạm tắt cron:

```bash
# [VPS] tắt
crontab -l | sed 's|^\([^#].*auto-deploy.sh.*\)|#\1|' | crontab -
crontab -l          # dòng auto-deploy phải có dấu # ở đầu

# [VPS] bật lại sau buổi demo
crontab -l | sed 's|^#\(.*auto-deploy.sh.*\)|\1|' | crontab -
```

### 0.2 Kiểm tra hệ thống

```bash
# [VPS] 16 container đều Up; 6 service Spring + frontend báo (healthy)
cd /home/huy/omnichannel-ecommerce
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# [VPS] health qua Nginx
curl -s https://huyecommerce.duckdns.org/actuator/health; echo
```

### 0.3 Làm nóng và chuẩn bị tài khoản

Order-service tự làm nóng Feign lúc khởi động và thử lại 1 lần khi identity lỗi tạm thời, nên không còn `503` ở đơn đầu; lời gọi gRPC đầu tiên vẫn hơi chậm. Chạy smoke test một lần trên máy mình (đi hết đăng ký → đặt hàng → thanh toán → `CONFIRMED`, in `OK` từng bước):

```powershell
# [PS] thư mục gốc repo
$env:BASE="https://huyecommerce.duckdns.org"; node scripts/smoke-test.mjs
```

**Ảnh sản phẩm:** đăng nhập admin → **Quản trị → Sản phẩm** → **Sửa** → mỗi phiên bản bấm **Tải ảnh** (JPEG/PNG/WebP ≤ 2 MB). Ảnh hiện ngay ở trang chủ, trang sản phẩm, giỏ hàng. Ảnh được lưu trên volume nên deploy lại không mất.

Chuẩn bị 2 tài khoản trên web: một tài khoản **khách** và một tài khoản **admin** (đăng ký như khách rồi cấp quyền):

```bash
# [VPS] cấp ADMIN cho email đã đăng ký trên web; đăng nhập lại để token mới có role ADMIN
docker exec -i ecommerce-postgres psql -U postgres -d identity_db -c \
  "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.email='admin-demo@test.com' AND r.name='ADMIN' ON CONFLICT DO NOTHING;"
```

Web tự làm mới access token (15 phút) bằng refresh token, không cần đăng nhập lại giữa buổi. Mở 2 cửa sổ: cửa sổ thường đăng nhập khách, cửa sổ ẩn danh đăng nhập admin; trang **Quản trị → Tồn kho** nhập sẵn hàng cho sản phẩm định demo.

### 0.4 Mở sẵn các tab

SSH tunnel để xem Mailpit, Grafana, RabbitMQ (chỉ bind `127.0.0.1` trên VPS):

```powershell
# [PS] giữ cửa sổ này mở suốt buổi demo
ssh -L 8025:127.0.0.1:8025 -L 3000:127.0.0.1:3000 -L 16686:127.0.0.1:16686 -L 15672:127.0.0.1:15672 huy@huyecommerce.duckdns.org
```

| Tab | Địa chỉ |
| --- | --- |
| Web (frontend) | https://huyecommerce.duckdns.org |
| Mailpit (hộp thư) | http://localhost:8025 |
| Grafana (admin / mật khẩu trong `.env`) | http://localhost:3000 |
| Jaeger (trace) | http://localhost:16686 |
| RabbitMQ Management | http://localhost:15672 |
| GitHub Actions | tab Actions của repo |
| Sơ đồ kiến trúc | `docs/architecture.png`, `docs/rabbitmq-topology.png` |

Xóa hộp thư Mailpit trước khi bắt đầu (nút **Delete all**) để chỉ còn thư của buổi demo.

---

## 1. Tổng quan dự án (3–4 phút)

Mở [Project_Overview.md](Project_Overview.md) (xem trên GitHub cho đẹp). Trình bày theo thứ tự:

1. **Bài toán** (mục 1): thương mại điện tử đa kênh, 3 yêu cầu kỹ thuật trọng tâm: không bán vượt kho, nhất quán dữ liệu giữa các service, chịu lỗi.
2. **Kiến trúc** (`architecture.png`):
   - 6 service, mỗi service một database.
   - Gateway là cửa vào duy nhất, sau Nginx HTTPS.
   - Đồng bộ: gRPC (Order → Product) và Feign (Order → Identity).
   - Bất đồng bộ: Saga qua RabbitMQ + Transactional Outbox.
3. **Con số chính** (mục 2): đọc nhanh bảng.
4. **Đã làm được** (mục 3): chỉ đọc tiêu đề 5 nhóm, nói "phần demo sẽ chứng minh từng nhóm".
5. **Chưa làm được** (mục 4): nói thẳng và nêu lý do. Ba điểm hội đồng hay hỏi:
   - chưa có app mobile (đã có web, API nhận `channel=MOBILE`);
   - thanh toán thật mới ở mức VNPAY sandbox;
   - không dùng MongoDB/Elasticsearch (tìm kiếm bằng `tsvector`).
6. Chuyển sang demo: "Giờ em chứng minh các phần trên chạy thật trên VPS."

## 2. Giao diện web và tài liệu API (1 phút)

Mở https://huyecommerce.duckdns.org: web **gom.** (Next.js) chạy cùng tên miền, Nginx chuyển `/` vào frontend và `/api/v1/` vào gateway. Mọi request của web đi qua gateway nên vẫn qua JWT và rate limit.

Tài liệu API: mở `docs/api/order-service.openapi.json` trên GitHub. Nói: spec OpenAPI 3.1 do từng service tự sinh từ code (springdoc), xuất ra repo bằng `scripts/export-openapi.sh`; prod không mở Swagger UI để không lộ bề mặt API thừa.

## 2b. Phần mở rộng: tìm kiếm, Idempotency-Key, VNPAY (3 phút)

1. **Tìm kiếm toàn văn:** ô tìm kiếm trên web, gõ `dien thoai` (không dấu) rồi `iph` (gõ dở) → vẫn ra kết quả. Nói: cột `search_vector` generated + GIN index, `unaccent`, mỗi từ khớp tiền tố.
2. **Idempotency-Key:** web sinh một key cho mỗi lần bấm **Đặt hàng** và giữ nguyên key khi bấm lại sau lỗi mạng. Chứng minh ở terminal: dòng `POST /orders cung Idempotency-Key -> 200, Idempotent-Replayed=true` của `smoke-test.mjs` (cùng `id` đơn, kho không bị trừ lần hai). Đổi nội dung mà giữ key thì server trả `422 IDEMPOTENCY_KEY_REUSED`.
3. **VNPAY sandbox** (nếu đã cấu hình): ở trang đơn hàng chọn **VNPAY Sandbox** → **Thanh toán** → trang VNPAY → thẻ test NCB `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456` (đối chiếu với thẻ test trong email đăng ký sandbox). VNPAY trả về trang `/payment/vnpay-return` của web, hiện "Thanh toán VNPAY thành công" → **Xem đơn hàng** → `Đã xác nhận / Đã thanh toán`. Log: `docker logs ecommerce-payment-service 2>&1 | grep VNPAY`.

## 3. S1 — Đặt hàng: thanh toán khi nhận hàng (COD) và VNPAY (3 phút)

Trên web, tài khoản khách:

1. Chọn sản phẩm → phiên bản → **Mua ngay** → chọn địa chỉ, **Thanh toán khi nhận hàng (COD)** → **Đặt hàng**. Nói: trong 1 transaction, `orders` + `order_items` được ghi, kho giữ bằng `UPDATE ... WHERE available_qty >= :q`, event `ORDER_CREATED` ghi vào outbox.
2. Trang đơn tự gửi lựa chọn COD; khoảng 1–2 giây sau đơn thành `Đã xác nhận` + badge `Thu tiền khi giao (COD)`. Khung **Theo dõi Saga**: payment-service ghi COD và phát `payment.cod_accepted` qua outbox → order-service xác nhận đơn, hàng giữ chuyển thành đã bán, **chưa thu tiền**. Mailpit: "Đã nhận đơn hàng" và "Đặt hàng thành công (thanh toán khi nhận hàng)".
3. Đặt thêm 1 đơn chọn **VNPAY** → chuyển sang trang VNPAY sandbox (có cả lựa chọn quét **VNPAY-QR** bằng app ngân hàng) → thẻ test NCB `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456` → về trang `/payment/vnpay-return` → **Xem đơn hàng**: `Đã xác nhận / Đã thanh toán`. Chữ ký VNPAY (HMAC-SHA512) được kiểm ở payment-service; IPN và return URL đi chung một hàm áp kết quả có chống lặp.
4. Xem dấu vết trên cả 3 database (mã đơn đầy đủ lấy từ URL trang đơn hàng):

   ```bash
   # [VPS] trong /home/huy/omnichannel-ecommerce
   docker exec -i ecommerce-postgres psql -U postgres -d order_db -v order_id=<orderId> < scripts/sql/saga-check.sql
   ```

   Chỉ ra: outbox của order có `ORDER_CREATED`, `ORDER_CONFIRMED` đều `SENT`; payment VNPAY `SUCCESS` (COD: `PENDING` tới khi giao xong); notification tương ứng.

## 4. S2 — Thanh toán thất bại, trả kho (2 phút)

1. Cửa sổ admin: **Quản trị → Tồn kho**, chọn sản phẩm, ghi lại số **Còn bán**.
2. Cửa sổ khách: đặt 1 đơn sản phẩm đó, chọn **VNPAY** → cửa sổ admin bấm lại sản phẩm: **Còn bán** giảm 1, **Đang giữ** tăng 1.
3. Trên trang VNPAY bấm **Hủy thanh toán** → về web báo "không thành công" → đơn thành `Đã hủy / Thanh toán lỗi`, Saga hiện *Hủy đơn & trả hàng về kho*. Admin xem lại: kho về đúng số cũ. Mailpit có thư "đã bị hủy". (Cách khác: khách bấm **Hủy đơn** khi còn `Chờ thanh toán`.)

Nói thêm (không cần demo): đơn không thanh toán trong 15 phút bị job hủy và trả kho theo đúng đường này. Tiền về **sau khi** đơn đã hủy thì đơn không được "sống lại", payment bị đánh dấu `needs_refund` để đối soát.

## 5. S3 — Admin giao hàng (2 phút)

Cửa sổ admin, **Quản trị → Đơn hàng**, lọc **Đã xác nhận**:

- Bấm **Giao hàng** ở đơn COD của S1 → xác nhận → `Đang giao`; lọc **Đang giao** → **Hoàn tất** → `Hoàn thành`. Đơn COD chuyển `Đã thanh toán` (shipper đã thu tiền mặt), payment COD sang `SUCCESS` qua event `order.completed`. Trang đơn của khách hiện đủ 6 bước Saga.
- Chỉ có hai bước `CONFIRMED → SHIPPING → COMPLETED`, mỗi bước là một `UPDATE ... WHERE status = :from`: hai admin bấm cùng lúc thì một người nhận `409`.
- Tài khoản khách mở `/admin` → "Bạn không có quyền truy cập"; API admin trả `403` (dòng cuối của `smoke-test.mjs`).
- Mailpit: "đang được giao", "đã giao thành công".

## 6. Bảo mật (2 phút)

```bash
# [VPS] Không token -> 401
curl -s -o /dev/null -w "%{http_code}\n" https://huyecommerce.duckdns.org/api/v1/orders

# [VPS] Rate limit đăng nhập (3 req/s, burst 5): 20 request song song -> có 429
seq 1 20 | xargs -P 20 -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST https://huyecommerce.duckdns.org/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"x@test.com","password":"wrong"}' | sort | uniq -c

# [VPS] Actuator khác health bị Nginx chặn
curl -s -o /dev/null -w "%{http_code}\n" https://huyecommerce.duckdns.org/actuator/prometheus   # 404
```

Nói: rate limit theo `userId` (có JWT) hoặc theo IP lấy từ phần tử **cuối** của `X-Forwarded-For` do Nginx ghi, nên client không giả IP để né được. DB, Redis, RabbitMQ chỉ bind `127.0.0.1`.

## 7. Chịu lỗi: RabbitMQ chết vẫn nhận đơn (2 phút, tùy thời gian)

```bash
# [VPS]
docker stop ecommerce-rabbitmq
```

Đặt 1 đơn trên web → vẫn tạo được, trang đơn hàng hiện `Chờ thanh toán`, Saga đứng ở bước *Khởi tạo thanh toán*. Chạy `saga-check.sql` cho đơn đó: outbox `ORDER_CREATED` đang `PENDING`, chưa có payment.

```bash
# [VPS]
docker start ecommerce-rabbitmq
```

Đợi khoảng 30 giây, chạy lại `saga-check.sql`: outbox đã `SENT`, payment đã tạo, Mailpit có thư. Đây là lý do dùng outbox thay vì publish thẳng trong request.

### 7b. Tắt Notification Service, người dùng vẫn đặt hàng được (tiêu chí đề tài)

```bash
# [VPS]
docker stop ecommerce-notification-service
```

Trên web đặt 1 đơn → vẫn tạo được, trang đơn hàng hiện bình thường; thanh toán vẫn chạy (payment và order không phụ thuộc notification). Mở RabbitMQ Management → queue `notification.order-events` có message đang chờ, Mailpit chưa có thư.

```bash
# [VPS]
docker start ecommerce-notification-service
```

Khoảng 30–60 giây sau khi service healthy: queue về 0, Mailpit nhận thư của đơn vừa đặt. Nói: queue durable giữ event trong lúc consumer chết; consumer chống trùng theo `messageId` nên nhận lại không gửi 2 thư. Đã chạy thử 14/10: lúc tắt queue = 1, mail không đổi; bật lại queue = 0, mail +1.

## 8. Giám sát và load test (2 phút)

- **Grafana** (dashboard "Omnichannel - Tổng quan"): request/giây, p95 theo service, mã HTTP ở gateway, heap JVM, connection DB, message đang chờ trong từng queue.
- **Log tập trung (Loki):** Grafana → *Explore* → Loki → `{service="order-service"}` hoặc `{level="ERROR"}`. Log của 16 container về một chỗ, không cần `docker logs` từng cái.
- **Trace (Jaeger):** đặt 1 đơn trên web, mở DevTools → response có `traceId` → dán vào Jaeger. Trace `POST /api/v1/orders` có khoảng 8 span: gateway (kèm Redis rate limit) → order-service → identity (Feign, lấy địa chỉ) và product (gRPC `BatchGetSkus`). Từ span bấm *Logs for this span* để xem log cùng traceId trong Loki.
- **Load test** (đã chạy 13/10 trên máy local, chi tiết ở `Flashsale_Results.md`). Mở ảnh Grafana và tổng kết k6 đã chụp, nói 3 ý:
  1. Đúng 1000 đơn cho kho 1000 dưới tải mục tiêu 1000 req/s; `load-verify.sql` cả 4 cột `t`.
  2. Hết hàng: giữ 1000 req/s, trung vị 10 ms, nhờ bước từ chối sớm.
  3. Còn hàng: khoảng 30 đơn/giây cho một SKU, vì các đơn cùng SKU đi tuần tự qua khóa. Chọn đúng trước nhanh sau; nêu hướng cải thiện.
  4. Nói rõ phạm vi: "Hệ thống giữ được tính đúng đắn dưới tải mục tiêu 1.000 request/giây và xử lý nhanh đường từ chối khi hết hàng; chưa chứng minh được khả năng tạo 500–1.000 đơn thành công mỗi giây." Không nói "chịu 1000 đơn/giây".
- Kết quả tính đúng 100 người / kho 10: `docs/Flashsale_Results.md`.

## 9. CI/CD (1 phút)

Tab **Actions**: workflow "Docker Build & Push" chạy test → build 7 image (6 service + frontend) → push GHCR với tag `sha-xxxxxxx` và `latest`. Trên VPS:

```bash
# [VPS] log của cron (đường dẫn sau dấu >> trong crontab -l)
crontab -l | grep auto-deploy
tail -20 <đường dẫn log ở trên>
```

Nói lý do không dùng SSH-push: nhà cung cấp VPS chặn kết nối quốc tế vào cổng 22 (đã xác minh bằng tcpdump), nên VPS tự kéo image mỗi 2 phút (xem `docs/Scope_Adjustments.md` mục 6).

---

## Câu hỏi dễ gặp

| Câu hỏi | Trả lời ngắn |
| --- | --- |
| Sao không bán vượt kho khi 1000 người mua cùng lúc? | `UPDATE inventory ... WHERE available_qty >= :q` (PostgreSQL khóa dòng) + `CHECK (available_qty >= 0)`. Redisson lock chỉ giảm tranh chấp, tắt lock vẫn đúng (Flashsale_Results lần 2). |
| Chịu 1000 req/s mà sao chỉ bán khoảng 30 đơn/giây? | 1000 req/s là tải đến; ở đỉnh, kho đã hết nên gần như toàn bộ là `409` trả sớm. Em chỉ khẳng định giữ đúng dưới tải đó, chưa chứng minh 500–1.000 đơn thành công/giây. Mọi đơn mua cùng một SKU nên phải đi tuần tự qua khóa kho; đó là cái giá của "không bán vượt". Request chờ quá 3 giây nhận `409 INVENTORY_BUSY` để client thử lại, không treo. Muốn nhanh hơn: chia kho một SKU thành nhiều dòng, hoặc xếp hàng đơn qua queue. |
| Event gửi 2 lần thì sao? | Consumer idempotent: notification ghi `processed_messages(messageId)` cùng transaction; payment có `order_id UNIQUE`, `gateway_txn_id UNIQUE`. |
| Sao không publish thẳng lên RabbitMQ? | DB commit mà broker chết thì mất event (hoặc ngược lại). Outbox ghi cùng transaction, relay gửi sau, có publisher confirm. Demo mục 7. |
| Thanh toán thật? | VNPAY sandbox qua `VnpayAdapter` (ký HMAC-SHA512, IPN + return URL), cùng interface `PaymentGatewayAdapter` với MOCK; cả hai đi chung một hàm áp kết quả có chống lặp. Môi trường thật chỉ khác TMN code / secret (Scope_Adjustments mục 8). |
| Người dùng bấm "Đặt hàng" 2 lần / mạng chập chờn? | Client gửi `Idempotency-Key` (UUID mỗi lần bấm, giữ nguyên khi gửi lại). Server lưu key cùng transaction với đơn; gửi lại → trả đúng đơn cũ, không giữ kho lần hai. Hai request trùng key song song: bên sau chờ ở khóa chính rồi nhận đơn của bên trước. |
| Chạy 2 instance order-service thì relay có gửi trùng? | Không: mỗi relay nhận lô bằng `SELECT ... FOR UPDATE SKIP LOCKED` + `locked_until`, hai instance nhận hai lô khác nhau; instance chết thì hết 60 giây lô tự được nhận lại (Messaging_Topology 5.1). |
| Tìm kiếm làm thế nào? | `tsvector` generated column + GIN index, bỏ dấu bằng `unaccent`, mỗi từ khớp tiền tố (`iph` → iPhone). Elasticsearch để sau, sẽ đồng bộ bằng event `product.*`. |
| Sao gộp Inventory vào Order? | Giữ hàng phải nguyên tử với tạo đơn; tách ra thành giao dịch phân tán (Scope_Adjustments 3.3). |
| Tiền về sau khi đơn bị hủy? | Đơn không hồi sinh vì hàng đã trả kho cho người khác; payment đánh dấu `needs_refund` để đối soát thủ công. |

## Sự cố trong lúc demo

| Hiện tượng | Xử lý |
| --- | --- |
| `503 ..._UNAVAILABLE` khi tạo đơn | Lỗi cold-start Feign đã sửa (warm-up + thử lại 1 lần), nên nếu vẫn gặp thì service phía sau đang down thật: xem `docker ps` và log identity/product. Bấm đặt hàng lại an toàn (web giữ cùng `Idempotency-Key`, không tạo đơn trùng). |
| Web báo "Phiên đăng nhập đã hết hạn" | Refresh token hết hạn hoặc bị thu hồi: đăng nhập lại trên modal vừa hiện ra. |
| Đơn vẫn `Chờ thanh toán` sau khi bấm thành công | Đợi 3–5 giây, trang tự hỏi lại mỗi 2 giây (relay chạy theo chu kỳ). |
| Không có thư trong Mailpit | Kiểm tra tunnel còn mở; `docker logs ecommerce-notification-service --tail 20` tìm dòng `[EMAIL ĐÃ GỬI]`. |
| Trang không mở được từ mạng ngoài Việt Nam | Nhà cung cấp VPS chặn kết nối quốc tế; dùng mạng trong nước (4G điện thoại). |
| Trình duyệt báo chứng chỉ lạ | Phần mềm diệt virus (Kaspersky) chặn HTTPS; xem bằng điện thoại 4G hoặc `openssl s_client` trên VPS. |
