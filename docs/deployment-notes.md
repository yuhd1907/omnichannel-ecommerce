# Deployment notes

Các thao tác vận hành cần làm bằng tay. Lệnh chạy trên máy đang chạy Docker Compose.

## Deploy tự động (CD)

1. Merge vào `main` → GitHub Actions **Docker Build & Push** chạy test, build 6 image, push GHCR với tag `sha-xxxxxxx` và `latest`.
2. Trên VPS, cron chạy `/home/huy/deploy/auto-deploy.sh` (bản sao của [scripts/auto-deploy.sh](../scripts/auto-deploy.sh)) mỗi 2 phút: `git reset --hard origin/main` + `docker compose pull`. Code hoặc image đổi thì `up -d --remove-orphans`.

```bash
crontab -l | grep auto-deploy                       # đường dẫn log nằm sau dấu >>
tail -f /home/huy/deploy/auto-deploy.log            # chờ dòng "deploy: code xxx -> yyy"
```

Mỗi lần deploy các service khởi động lại 1–3 phút. **Trước buổi demo tạm tắt cron** (lệnh ở [Demo_Script.md](Demo_Script.md) mục 0.1). File `.env` trên VPS không nằm trong git nên `git reset` không đụng tới; biến mới (vd `GRAFANA_ADMIN_PASSWORD`) phải tự thêm trước khi merge.

## Giám sát và công cụ demo

Mailpit, Prometheus, Grafana, RabbitMQ UI chỉ bind `127.0.0.1` trên VPS. Xem từ máy mình qua SSH tunnel (dùng đúng cách SSH thường dùng: WSL, key `-i`, hoặc app có port forwarding):

```bash
ssh -L 8025:127.0.0.1:8025 -L 3000:127.0.0.1:3000 -L 15672:127.0.0.1:15672 huy@huyecommerce.duckdns.org
```

Tắt Docker local trước (`docker compose stop`) để cổng 8025/3000 trên máy không bị container local chiếm. Grafana đăng nhập `admin` / `GRAFANA_ADMIN_PASSWORD`; RabbitMQ dùng `RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS` trong `.env` của VPS.

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

Nginx trên VPS đóng vai trò Reverse Proxy duy nhất đón request từ Internet (cổng 80 và 443), chuyển tiếp vào `api-gateway` tại `127.0.0.1:8080`.

File cấu hình mẫu lưu tại [deploy/nginx/omnichannel.conf](../deploy/nginx/omnichannel.conf).

**1. Áp dụng cấu hình Nginx trên VPS**

File đang chạy trên VPS là `/etc/nginx/sites-available/ecommerce.conf` (Certbot đã ghi vào file này). Khi sửa `deploy/nginx/omnichannel.conf` trong repo, cron deploy **không** tự cập nhật Nginx; phải chép tay:

```bash
cd /home/huy/omnichannel-ecommerce
sudo cp /etc/nginx/sites-available/ecommerce.conf ~/ecommerce.conf.bak      # sao lưu
sudo cp deploy/nginx/omnichannel.conf /etc/nginx/sites-available/ecommerce.conf
sudo nginx -t && sudo systemctl reload nginx
```

Chỉ giữ **một** file bật trong `/etc/nginx/sites-enabled/` cho tên miền này; hai file cùng `server_name` thì Nginx báo `conflicting server name ... ignored` và chỉ dùng file đọc trước. Lỗi thì khôi phục bằng `~/ecommerce.conf.bak`.

Cấu hình chặn mọi `/actuator/*` trừ `/actuator/health` (metrics chỉ Prometheus trong mạng Docker được đọc).

**2. Chứng chỉ SSL Let's Encrypt (DNS-01 qua DuckDNS)**

Do hạ tầng mạng của nhà cung cấp VPS chặn chiều kết nối quốc tế vào cổng 80 (khiến HTTP-01 challenge bị timeout), chứng chỉ SSL được cấp qua phương thức **DNS-01 challenge** (xác thực qua bản ghi TXT của DuckDNS):

```bash
sudo certbot certonly --manual --preferred-challenges dns -d huyecommerce.duckdns.org
```

Certbot sẽ yêu cầu tạo bản ghi TXT `_acme-challenge.huyecommerce.duckdns.org` trên DuckDNS. Cập nhật qua DuckDNS API:

```bash
curl "https://www.duckdns.org/update?domains=huyecommerce&token=<DUCKDNS_TOKEN>&txt=<CHUO_TXT_TU_CERTBOT>"
```

Sau khi xác thực thành công, chứng chỉ được lưu tại `/etc/letsencrypt/live/huyecommerce.duckdns.org/`.

**3. Gia hạn chứng chỉ (thủ công mỗi 90 ngày)**

Chứng chỉ Let's Encrypt có thời hạn 90 ngày. Do dùng DNS-01 thủ công, trước khi hết hạn thực hiện chạy lại lệnh certonly ở trên để cập nhật chứng chỉ mới, sau đó reload Nginx:

```bash
sudo systemctl reload nginx
```

## VNPAY sandbox trên VPS

1. Đăng ký tại https://sandbox.vnpayment.vn/devreg/ → email gửi `vnp_TmnCode`, `vnp_HashSecret`, tài khoản trang quản trị merchant và thẻ test.
2. Thêm vào `.env` trên VPS rồi `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d payment-service`:

   ```
   VNPAY_TMN_CODE=...
   VNPAY_HASH_SECRET=...
   VNPAY_RETURN_URL=https://huyecommerce.duckdns.org/api/v1/payments/vnpay/return
   ```
3. Trang quản trị merchant sandbox → cấu hình **IPN URL** `https://huyecommerce.duckdns.org/api/v1/payments/vnpay/ipn`. Máy chủ VNPAY ở Việt Nam nên vào được VPS.
4. Kiểm tra: `docker logs ecommerce-payment-service 2>&1 | grep VNPAY` — mỗi giao dịch có dòng `VNPAY ipn: txnRef=... responseCode=00 -> PROCESSED` (return URL về sau thì `-> DUPLICATE`, bình thường).

