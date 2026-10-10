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
