# Deployment notes

Các thao tác vận hành cần làm bằng tay. Lệnh chạy trên máy đang chạy Docker Compose.

## Outbox event bị FAILED

`OutboxRelay` (order-service) chuyển event sang `FAILED` sau 10 lần gửi lỗi (broker NACK, message bị return vì không có queue nào nhận, hoặc quá 5 giây không có publisher confirm). Event `FAILED` **không tự gửi lại**. Broker chết hẳn thì event vẫn giữ `PENDING`, không bị tính lỗi.

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
UPDATE outbox_events SET status = 'PENDING', retry_count = 0 WHERE status = 'FAILED';
```

```powershell
docker exec ecommerce-postgres psql -U postgres -d order_db -c "UPDATE outbox_events SET status='PENDING', retry_count=0 WHERE status='FAILED'"
```

Relay sẽ gửi lại trong vòng 1 giây. Việc gửi lại an toàn vì consumer chống trùng theo `message_id` (= `outbox_events.id`).

## Message trong DLQ

Message xử lý lỗi sau 3 lần thử, hoặc message hỏng, nằm ở `<queue>.dlq` (ví dụ `notification.order-events.dlq`). Xem bằng Management UI tại http://localhost:15672 → *Queues* → chọn DLQ → *Get messages*. Header `x-death` cho biết lý do. Sau khi sửa lỗi ở consumer, chuyển message về queue gốc bằng *Move messages*. Nút này chỉ có khi đã bật plugin shovel (image mặc định không bật):

```powershell
docker exec ecommerce-rabbitmq rabbitmq-plugins enable rabbitmq_shovel rabbitmq_shovel_management
```

Xem chi tiết topology tại [Messaging_Topology.md](Messaging_Topology.md).

## Nginx + HTTPS

Nginx trên VPS đóng vai trò Reverse Proxy duy nhất đón request từ Internet (cổng 80 và 443), chuyển tiếp vào `api-gateway` tại `127.0.0.1:8080`.

File cấu hình mẫu lưu tại [deploy/nginx/omnichannel.conf](../deploy/nginx/omnichannel.conf).

**1. Áp dụng cấu hình Nginx trên VPS**

```bash
sudo cp deploy/nginx/omnichannel.conf /etc/nginx/sites-available/omnichannel.conf
sudo ln -sf /etc/nginx/sites-available/omnichannel.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

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

