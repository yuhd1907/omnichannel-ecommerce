# Kịch bản demo trực tiếp

Thời lượng mục tiêu: khoảng 15 phút demo, phần còn lại để hỏi đáp. Demo trên VPS `https://huyecommerce.duckdns.org`; phần load test chạy trên máy local vì VPS nhỏ.

Ký hiệu: **[VPS]** là lệnh bash chạy trên VPS (prompt `huy@azvps-...`), **[PS]** là PowerShell trên máy mình.

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
# [VPS] 12 container đều Up, 6 service Spring báo (healthy)
cd /home/huy/omnichannel-ecommerce
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# [VPS] health qua Nginx
curl -s https://huyecommerce.duckdns.org/actuator/health; echo
```

### 0.3 Làm nóng

Lần gọi Feign/gRPC đầu tiên sau khi khởi động chậm, có thể trả 503. Chạy trước **folder 1 → 2 → 3** của Postman collection `Omnichannel Smoke Test` (environment "VPS Smoke") một lần. Folder 2 vẫn cần lệnh SQL cấp ADMIN in ra ở Console sau SM02. Sau bước này đã có sẵn `adminToken`, `userToken`, `addressId` cho phần demo.

### 0.4 Mở sẵn các tab

SSH tunnel để xem Mailpit, Grafana, RabbitMQ (chỉ bind `127.0.0.1` trên VPS):

```powershell
# [PS] giữ cửa sổ này mở suốt buổi demo
ssh -L 8025:127.0.0.1:8025 -L 3000:127.0.0.1:3000 -L 15672:127.0.0.1:15672 huy@huyecommerce.duckdns.org
```

| Tab | Địa chỉ |
| --- | --- |
| Swagger UI | https://huyecommerce.duckdns.org/swagger-ui.html |
| Mailpit (hộp thư) | http://localhost:8025 |
| Grafana (admin / mật khẩu trong `.env`) | http://localhost:3000 |
| RabbitMQ Management | http://localhost:15672 |
| GitHub Actions | tab Actions của repo |
| Sơ đồ kiến trúc | `docs/architecture.png`, `docs/rabbitmq-topology.png` |

Xóa hộp thư Mailpit trước khi bắt đầu (nút **Delete all**) để chỉ còn thư của buổi demo.

---

## 1. Kiến trúc (1 phút)

Mở `docs/architecture.png`. Ý chính:

- 6 service Spring Boot, mỗi service một database (5 DB trên PostgreSQL; gateway không có DB).
- API Gateway là cửa vào duy nhất: xác thực JWT, rate limit bằng Redis. Trên VPS chỉ mở 22/80/443; Nginx là reverse proxy duy nhất.
- Đồng bộ: Order → Product qua **gRPC**, Order → Identity qua **REST (Feign)**.
- Bất đồng bộ: **Saga** qua RabbitMQ, mỗi bên ghi event vào **Transactional Outbox** cùng transaction với dữ liệu nghiệp vụ.

## 2. Swagger (1 phút)

Mở `/swagger-ui.html`, đổi qua lại 4 service ở ô **Select a definition** góc phải. Bấm **Authorize**, dán `userToken`, thử `GET /api/v1/orders`. Điểm cần nói: spec do từng service tự sinh, gateway chỉ gom lại; "Try it out" vẫn đi qua gateway nên vẫn qua JWT và rate limit.

## 3. S1 — Đặt hàng và thanh toán thành công (3 phút)

Postman, folder **3. S1** (hoặc folder 5 nếu đã chạy S1 lúc làm nóng):

1. **Tạo đơn** → `201`, `PENDING`. Nói: trong 1 transaction, `orders` + `order_items` được ghi, kho trừ bằng `UPDATE ... WHERE available_qty >= :q`, event `ORDER_CREATED` ghi vào outbox.
2. Mở **Mailpit**: có thư "Đã nhận đơn hàng #...". Thư đi theo đường order outbox → RabbitMQ → notification → SMTP.
3. **Xem payment** → `PENDING`: payment-service tự tạo payment khi nhận `order.created`.
4. **Checkout MOCK** rồi **Webhook SUCCESS**. Webhook ký HMAC-SHA256 (`X-Signature`); sai chữ ký sẽ bị từ chối.
5. **Kiểm tra đơn** → `CONFIRMED / PAID`. Mailpit có thêm "Thanh toán thành công".
6. Xem dấu vết trên cả 3 database (copy `orderId` từ Console của Postman):

   ```bash
   # [VPS] trong /home/huy/omnichannel-ecommerce
   docker exec -i ecommerce-postgres psql -U postgres -d order_db -v order_id=<orderId> < scripts/saga-check.sql
   ```

   Chỉ ra: outbox của order có `ORDER_CREATED`, `ORDER_CONFIRMED` đều `SENT`; payment `SUCCESS`; 2 dòng notification.

## 4. S2 — Thanh toán thất bại, trả kho (2 phút)

Folder **4. S2**. `S2-0` ghi lại tồn kho, webhook `FAILED`, đơn thành `CANCELLED / FAILED`, `S2-6` cho thấy kho về đúng số cũ. Mailpit có thư "đã bị hủy".

Nói thêm (không cần demo): đơn không thanh toán trong 15 phút bị job hủy và trả kho theo đúng đường này. Tiền về **sau khi** đơn đã hủy thì đơn không được "sống lại", payment bị đánh dấu `needs_refund` để đối soát.

## 5. S3 — Admin giao hàng (2 phút)

Folder **5. S3**:

- `S3-6`: user thường gọi API admin → `403`.
- `S3-8`: nhảy cóc `CONFIRMED → COMPLETED` → `409 INVALID_STATUS_TRANSITION`.
- `S3-9`, `S3-10`: `SHIPPING`, rồi `COMPLETED`. Mỗi bước là một `UPDATE ... WHERE status = :from`, nên hai admin bấm cùng lúc thì chỉ một người thắng.
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

Tạo 1 đơn trong Postman → vẫn `201`. Chạy `saga-check.sql` cho đơn đó: outbox `ORDER_CREATED` đang `PENDING`, chưa có payment.

```bash
# [VPS]
docker start ecommerce-rabbitmq
```

Đợi khoảng 30 giây, chạy lại `saga-check.sql`: outbox đã `SENT`, payment đã tạo, Mailpit có thư. Đây là lý do dùng outbox thay vì publish thẳng trong request.

## 8. Giám sát và load test (2 phút)

- **Grafana** (dashboard "Omnichannel - Tổng quan"): request/giây, p95 theo service, mã HTTP ở gateway, heap JVM, connection DB, message đang chờ trong từng queue.
- **Load test** (chạy trên máy local; xem `scripts/load-order-k6.js`): tăng 100 → 500 → 1000 req/s qua gateway, sau đó chạy `scripts/load-verify.sql` → 4 cột kiểm tra đều `t` (không âm kho, không bán vượt, bảo toàn tồn kho). Nếu không chạy trực tiếp thì mở ảnh chụp kết quả k6 và Grafana đã chuẩn bị sẵn.
- Kết quả tính đúng 100 người / kho 10: `docs/Flashsale_Results.md`.

## 9. CI/CD (1 phút)

Tab **Actions**: workflow "Docker Build & Push" chạy test → build 6 image → push GHCR với tag `sha-xxxxxxx` và `latest`. Trên VPS:

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
| Event gửi 2 lần thì sao? | Consumer idempotent: notification ghi `processed_messages(messageId)` cùng transaction; payment có `order_id UNIQUE`, `gateway_txn_id UNIQUE`. |
| Sao không publish thẳng lên RabbitMQ? | DB commit mà broker chết thì mất event (hoặc ngược lại). Outbox ghi cùng transaction, relay gửi sau, có publisher confirm. Demo mục 7. |
| Thanh toán thật? | MOCK sau interface `PaymentGatewayAdapter`; cổng thật cần merchant và URL webhook nhận được kết nối quốc tế (Scope_Adjustments mục 8). |
| Sao gộp Inventory vào Order? | Giữ hàng phải nguyên tử với tạo đơn; tách ra thành giao dịch phân tán (Scope_Adjustments 3.3). |
| Tiền về sau khi đơn bị hủy? | Đơn không hồi sinh vì hàng đã trả kho cho người khác; payment đánh dấu `needs_refund` để đối soát thủ công. |

## Sự cố trong lúc demo

| Hiện tượng | Xử lý |
| --- | --- |
| `503 ..._UNAVAILABLE` ở lần tạo đơn đầu | Service vừa khởi động, gửi lại request. Đã có bước làm nóng ở mục 0.3. |
| Đơn vẫn `PENDING` sau webhook | Đợi 3–5 giây rồi gửi lại "Kiểm tra đơn" (relay chạy theo chu kỳ). |
| Không có thư trong Mailpit | Kiểm tra tunnel còn mở; `docker logs ecommerce-notification-service --tail 20` tìm dòng `[EMAIL ĐÃ GỬI]`. |
| Trang không mở được từ mạng ngoài Việt Nam | Nhà cung cấp VPS chặn kết nối quốc tế; dùng mạng trong nước (4G điện thoại). |
| Trình duyệt báo chứng chỉ lạ | Phần mềm diệt virus (Kaspersky) chặn HTTPS; xem bằng điện thoại 4G hoặc `openssl s_client` trên VPS. |
