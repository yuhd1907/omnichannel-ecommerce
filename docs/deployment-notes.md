# Deployment notes

Các thao tác vận hành cần làm bằng tay. Lệnh chạy trên máy đang chạy Docker Compose.

## Deploy tự động (CD)

1. Merge vào `main` → GitHub Actions **Docker Build & Push** chạy test, build 7 image (6 service + `frontend`), push GHCR với tag `sha-xxxxxxx` và `latest`.
2. Job **Deploy to VPS** (GitHub Environment `production`) dùng `appleboy/ssh-action` (pin theo commit SHA) SSH vào VPS bằng các secret `VPS_HOST`, `VPS_PORT`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_HOST_FINGERPRINT`. Trên VPS: `git merge --ff-only` tới đúng commit vừa build, rồi chạy [scripts/deploy/deploy.sh](../scripts/deploy/deploy.sh) `sha-xxxxxxx`:
   - pull 7 image của tag đó (lỗi thì dừng, bản đang chạy không bị đụng tới);
   - `docker compose up -d --remove-orphans --wait` (chờ health check), gọi thử gateway `/actuator/health` và frontend;
   - đạt → ghi `IMAGE_TAG=sha-xxxxxxx` vào `.env` (lệnh `docker compose` chạy tay sau này dùng đúng bản đang chạy), xóa image cũ, chỉ giữ tag hiện tại và tag trước;
   - không đạt → `up` lại tag trước đó, job báo đỏ.

```bash
# [VPS] bản đang chạy
grep IMAGE_TAG /home/huy/omnichannel-ecommerce/.env
# [VPS] quay lại một bản cũ bằng tay (tag vẫn còn trên GHCR)
cd /home/huy/omnichannel-ecommerce && bash scripts/deploy/deploy.sh sha-abc1234
```

Rollback chỉ đổi image, **không** đảo ngược migration Flyway. Migration phải tương thích ngược: chỉ thêm cột/bảng, không xóa hay đổi tên trong cùng một lần deploy.

**Duyệt tay trước khi deploy (tùy chọn):** GitHub → *Settings* → *Environments* → `production` → bật *Required reviewers*, chọn chính mình. Mỗi lần merge `main`, job deploy dừng ở *Waiting* tới khi bấm *Approve*. Nên bật trong ngày demo để lỡ merge cũng không làm hệ thống khởi động lại.

Mỗi lần deploy các service khởi động lại 1–3 phút. File `.env` trên VPS không nằm trong git nên `git merge` không đụng tới (trừ dòng `IMAGE_TAG` do `deploy.sh` ghi); biến mới (vd `GRAFANA_ADMIN_PASSWORD`) phải tự thêm trước khi merge.

**Trước 15/10/2026** CD chạy kiểu pull (cron 2 phút trên VPS) vì nhà cung cấp VPS chặn kết nối quốc tế vào cổng 22; nay đã chuyển sang SSH và gỡ cron. Kiểm tra VPS không còn dòng cron cũ: `crontab -l | grep auto-deploy` (còn thì xóa bằng `crontab -l | grep -v auto-deploy | crontab -`).

## Giám sát và công cụ demo

Mailpit, Prometheus, Grafana, Jaeger, RabbitMQ UI chỉ bind `127.0.0.1` trên VPS; Loki và Promtail không mở cổng. Xem từ máy mình qua SSH tunnel (dùng đúng cách SSH thường dùng: WSL, key `-i`, hoặc app có port forwarding):

```bash
ssh -L 8025:127.0.0.1:8025 -L 3000:127.0.0.1:3000 -L 16686:127.0.0.1:16686 -L 15672:127.0.0.1:15672 huy@huyecommerce.duckdns.org
```

- **Metrics:** Grafana dashboard "Omnichannel - Tổng quan" (Prometheus).
- **Log tập trung:** Grafana → *Explore* → datasource **Loki**, ví dụ `{service="order-service", level="ERROR"}`. Promtail đọc log mọi container qua `docker.sock`, Loki giữ 3 ngày.
- **Trace:** http://localhost:16686 (Jaeger) hoặc Grafana → *Explore* → **Jaeger**. Mỗi response API có `traceId`; dán vào ô *Lookup by Trace ID* để xem request đi qua gateway → service → gRPC/Feign. Từ dòng log trong Loki bấm nút `traceId` để mở trace, từ span bấm *Logs for this span* để về log.
- Tỉ lệ lấy mẫu trace mặc định 100% (`TRACING_SAMPLING_PROBABILITY`), hạ xuống `0.1` trong `.env` khi chạy load test.

Tắt Docker local trước (`docker compose stop`) để cổng 8025/3000 trên máy không bị container local chiếm. Grafana đăng nhập `admin` / `GRAFANA_ADMIN_PASSWORD`; RabbitMQ dùng `RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS` trong `.env` của VPS.

## Email (SMTP)

notification-service gửi email qua SMTP, lưu từng thư trong bảng `notifications` (cột `email_*`) như một outbox:

| `email_status` | Ý nghĩa |
| --- | --- |
| `PENDING` | Chờ gửi / chờ gửi lại (`email_next_attempt_at`) |
| `SENT` | Đã gửi (`email_sent_at`) |
| `FAILED` | Lỗi 5 lần liên tiếp (backoff 1, 5, 15, 30 phút), cần xem `email_last_error` |
| `SKIPPED` | Không có địa chỉ nhận hoặc `MAIL_ENABLED=false` |

```bash
# Thư lỗi / đang chờ
docker exec ecommerce-postgres psql -U postgres -d notification_db -c \
  "SELECT type, email_to, email_status, email_attempts, email_last_error FROM notifications WHERE email_status IN ('PENDING','FAILED') ORDER BY created_at DESC LIMIT 20"
# Gửi lại thư FAILED sau khi sửa cấu hình SMTP
docker exec ecommerce-postgres psql -U postgres -d notification_db -c \
  "UPDATE notifications SET email_status='PENDING', email_attempts=0, email_next_attempt_at=now() WHERE email_status='FAILED'"
```

**Mặc định dùng Mailpit** (hộp thư giả, xem ở cổng 8025). **Gửi thư thật**: thêm vào `.env` rồi `docker compose up -d notification-service`:

```
# Gmail: bật xác minh 2 bước -> https://myaccount.google.com/apppasswords tạo App Password 16 ký tự
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_USERNAME=<địa chỉ gmail>
SMTP_PASSWORD=<app password>
MAIL_FROM=<địa chỉ gmail>
APP_PUBLIC_URL=https://huyecommerce.duckdns.org
```

Brevo (300 thư/ngày miễn phí): `SMTP_HOST=smtp-relay.brevo.com`, user/key lấy ở mục *SMTP & API*. Không dùng mật khẩu đăng nhập Gmail; App Password có thể thu hồi riêng. Khi chạy load test đặt `MAIL_ENABLED=false` để không gửi hàng nghìn thư.

## Ảnh sản phẩm

Admin tải ảnh ở **Quản trị → Sản phẩm → Sửa** (mỗi phiên bản có nút Tải ảnh) (JPEG/PNG/WebP, tối đa 2 MB/ảnh; server kiểm tra chữ ký file). Database chỉ lưu **URL** ảnh (`product_skus.image_url`, `products.image_url`), nơi lưu file chọn bằng `.env`:

| `CLOUDINARY_URL` | Nơi lưu file | URL lưu trong DB |
| --- | --- | --- |
| trống (mặc định) | Volume Docker `product_uploads` (`/data/uploads` của product-service) | `/api/v1/products/images/<tên>` (gateway không rate limit route này) |
| `cloudinary://<key>:<secret>@<cloud>` | Cloudinary (object storage + CDN), thư mục `gom/products` | `https://res.cloudinary.com/<cloud>/image/upload/f_auto,q_auto,c_limit,w_1200/...` |

Bật Cloudinary: tạo tài khoản miễn phí tại https://cloudinary.com → *Console → Dashboard* → copy dòng **API environment variable** → dán vào `.env` (local và VPS) dạng `CLOUDINARY_URL=cloudinary://...` → `docker compose up -d product-service`. Log khởi động in `Product images: Cloudinary (cloud=...)`. Upload có chữ ký SHA-1 phía server, `api_secret` không xuống trình duyệt. Ảnh đã lưu trên volume trước đó vẫn hiển thị bình thường. Ảnh SKU đầu tiên của một sản phẩm thành ảnh đại diện ở trang chủ. Sao lưu ảnh trên VPS:

```bash
docker run --rm -v omnichannel-ecommerce_product_uploads:/data -v "$PWD":/backup alpine tar czf /backup/product-images.tgz -C /data .
```

## Outbox event bị FAILED

`OutboxRelay` (order-service; payment-service và product-service giống hệt, đổi `order_db` thành `payment_db` / `product_db`) chuyển event sang `FAILED` sau 10 lần gửi lỗi (broker NACK, message bị return vì không có queue nào nhận, hoặc quá 5 giây không có publisher confirm). Event `FAILED` **không tự gửi lại**. Broker chết hẳn thì event vẫn giữ `PENDING`, không bị tính lỗi.

**1. Xem event lỗi**

```powershell
docker exec ecommerce-postgres psql -U postgres -d order_db -c "SELECT id, event_type, aggregate_id, retry_count, created_at FROM outbox_events WHERE status = 'FAILED' ORDER BY created_at"
```

Log của order-service ghi lý do của từng lần thử (`Outbox relay: retry n/10 ...: <lý do>`):

```powershell
docker logs ecommerce-order-service 2>&1 | Select-String "Outbox relay"
```

**2. Sửa nguyên nhân trước.** Lý do thường gặp là `returned 312 NO_ROUTE`: chưa có queue nào bind routing key đó, tức là service nhận chưa chạy lần nào để khai báo queue. Khởi động service nhận rồi kiểm tra binding:

```powershell
docker exec ecommerce-rabbitmq rabbitmqctl list_bindings -q source_name destination_name routing_key
```

**3. Đưa event về hàng đợi gửi lại**

```sql
UPDATE outbox_events SET status = 'PENDING', retry_count = 0, locked_until = NULL WHERE status = 'FAILED';
```

```powershell
docker exec ecommerce-postgres psql -U postgres -d order_db -c "UPDATE outbox_events SET status='PENDING', retry_count=0, locked_until=NULL WHERE status='FAILED'"
```

Relay sẽ gửi lại trong vòng 1 giây. Event của product-service (`product.sku.created`) bị `FAILED` vì `NO_ROUTE` thường do order-service chưa khởi động lần nào để khai báo queue `order.product-events`. Việc gửi lại an toàn vì consumer chống trùng theo `message_id` (= `outbox_events.id`).

## Message trong DLQ

Message xử lý lỗi sau 3 lần thử, hoặc message hỏng, nằm ở `<queue>.dlq` (ví dụ `notification.order-events.dlq`). Xem bằng Management UI tại http://localhost:15672 (VPS: qua SSH tunnel ở trên) → *Queues* → chọn DLQ → *Get messages*. Header `x-death` cho biết lý do. Sau khi sửa lỗi ở consumer, chuyển message về queue gốc bằng *Move messages*. Nút này chỉ có khi đã bật plugin shovel (image mặc định không bật):

```powershell
docker exec ecommerce-rabbitmq rabbitmq-plugins enable rabbitmq_shovel rabbitmq_shovel_management
```

Xem chi tiết topology tại [Messaging_Topology.md](Messaging_Topology.md).

## Nginx + HTTPS

Nginx trên VPS đóng vai trò Reverse Proxy duy nhất đón request từ Internet (cổng 80 và 443): `/api/v1/` và `/actuator/health` vào `api-gateway` (`127.0.0.1:8080`), mọi đường dẫn còn lại vào `frontend` (`127.0.0.1:3001`).

File cấu hình mẫu lưu tại [infra/nginx/omnichannel.conf](../infra/nginx/omnichannel.conf).

**1. Áp dụng cấu hình Nginx trên VPS**

File đang chạy trên VPS là `/etc/nginx/sites-available/ecommerce.conf` (Certbot đã ghi vào file này). Khi sửa `infra/nginx/omnichannel.conf` trong repo, CD **không** tự cập nhật Nginx; phải chép tay:

```bash
cd /home/huy/omnichannel-ecommerce
sudo cp /etc/nginx/sites-available/ecommerce.conf ~/ecommerce.conf.bak      # sao lưu
sudo cp infra/nginx/omnichannel.conf /etc/nginx/sites-available/ecommerce.conf
sudo nginx -t && sudo systemctl reload nginx
```

Chỉ giữ **một** file bật trong `/etc/nginx/sites-enabled/` cho tên miền này; hai file cùng `server_name` thì Nginx báo `conflicting server name ... ignored` và chỉ dùng file đọc trước. Lỗi thì khôi phục bằng `~/ecommerce.conf.bak`.

Cấu hình chặn mọi `/actuator/*` trừ `/actuator/health` (metrics chỉ Prometheus trong mạng Docker được đọc).

**2. Chứng chỉ SSL Let's Encrypt (HTTP-01, tự gia hạn)**

Lần đầu, khi nhà cung cấp VPS còn chặn kết nối quốc tế vào cổng 80, chứng chỉ phải cấp thủ công qua DNS-01 (bản ghi TXT DuckDNS). Sau khi nhà cung cấp mở kết nối quốc tế, chứng chỉ được cấp lại bằng plugin Nginx của Certbot (HTTP-01) và **tự gia hạn**:

```bash
sudo certbot --nginx -d huyecommerce.duckdns.org
sudo certbot renew --dry-run          # kiểm tra gia hạn tự động
systemctl list-timers | grep certbot  # certbot.timer chạy 2 lần/ngày
```

Chứng chỉ nằm ở `/etc/letsencrypt/live/huyecommerce.duckdns.org/` (hết hạn 2027-01-10, tự gia hạn trước 30 ngày).

**3. Tường lửa và SSH**

`ufw` chỉ mở 22, 80, 443. SSH tắt đăng nhập root và mật khẩu (`PermitRootLogin no`, `PasswordAuthentication no`), `fail2ban` chặn IP dò mật khẩu SSH (`sudo fail2ban-client status sshd`).

## Áp dụng bản cập nhật frontend + Loki/Jaeger lên VPS

CD tự cập nhật code và image, nhưng các bước sau phải làm tay **một lần**:

1. **GHCR:** sau lần build đầu, package `omnichannel-frontend` mặc định là *private*. Vào GitHub → *Packages* → `omnichannel-frontend` → *Package settings* → *Change visibility* → đặt cùng quyền với 6 image cũ (Public, hoặc giữ Private nếu VPS đã `docker login ghcr.io`). Nếu VPS không kéo được image này, `docker compose pull` báo lỗi và job deploy dừng (bản đang chạy giữ nguyên).
2. **`.env` trên VPS:** thêm `DEPLOY_ENV=prod` và đổi `VNPAY_RETURN_URL=https://huyecommerce.duckdns.org/payment/vnpay-return`. `PAYMENT_WEBHOOK_SECRET` đã có sẵn (frontend dùng chung để ký webhook cổng MOCK).
3. **Nginx:** chép cấu hình mới (`/` → frontend, `/api/v1/` → gateway) theo mục *Nginx + HTTPS* ở trên rồi `sudo nginx -t && sudo systemctl reload nginx`.
4. **RAM:** frontend + Loki + Promtail + Jaeger thêm khoảng 1 GB (giới hạn 256M + 256M + 128M + 384M). Kiểm tra `free -h` và `docker stats --no-stream` sau khi lên.

## VNPAY sandbox trên VPS

1. Đăng ký tại https://sandbox.vnpayment.vn/devreg/ → email gửi `vnp_TmnCode`, `vnp_HashSecret`, tài khoản trang quản trị merchant và thẻ test.
2. Thêm vào `.env` trên VPS rồi `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d payment-service`:

   ```
   VNPAY_TMN_CODE=...
   VNPAY_HASH_SECRET=...
   VNPAY_RETURN_URL=https://huyecommerce.duckdns.org/payment/vnpay-return
   ```
3. Trang quản trị merchant sandbox → cấu hình **IPN URL** `https://huyecommerce.duckdns.org/api/v1/payments/vnpay/ipn`. Máy chủ VNPAY ở Việt Nam nên vào được VPS.
4. Kiểm tra: `docker logs ecommerce-payment-service 2>&1 | grep VNPAY` — mỗi giao dịch có dòng `VNPAY ipn: txnRef=... responseCode=00 -> PROCESSED` (return URL về sau thì `-> DUPLICATE`, bình thường).

