-- Xem toan bo dau vet Saga cua MOT don hang tren 3 database (order -> payment -> notification).
-- Dung khi demo: tao don, thanh toan / huy, roi chay:
--   Get-Content scripts\saga-check.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db -v order_id=<UUID>
-- Tren VPS (bash):
--   docker exec -i ecommerce-postgres psql -U postgres -d order_db -v order_id=<UUID> < scripts/saga-check.sql
\if :{?order_id}
\else
  \echo 'Thieu bien: them -v order_id=<UUID don hang>'
  \quit
\endif
\pset footer off

\echo
\echo '===== [order_db] 1. Don hang ====='
SELECT id, status, payment_status, total_amount, customer_email, created_at, updated_at
FROM orders WHERE id = :'order_id';

\echo '===== [order_db] 2. Mat hang + ton kho hien tai cua tung SKU ====='
SELECT oi.sku_code, oi.quantity, i.available_qty, i.reserved_qty
FROM order_items oi
LEFT JOIN inventory i ON i.sku_code = oi.sku_code
WHERE oi.order_id = :'order_id';

\echo '===== [order_db] 3. Outbox: event order da phat (SENT = da len RabbitMQ) ====='
SELECT event_type, status, retry_count, created_at, published_at
FROM outbox_events WHERE aggregate_id = :'order_id'
ORDER BY created_at;

\c payment_db
\echo '===== [payment_db] 4. Payment cua don ====='
SELECT id, status, amount, method, needs_refund, created_at, updated_at
FROM payments WHERE order_id = :'order_id';

\echo '===== [payment_db] 5. Giao dich tu webhook (gateway_txn_id UNIQUE chong webhook lap) ====='
SELECT t.gateway_txn_id, t.status, t.amount, t.created_at
FROM payment_transactions t
JOIN payments p ON p.id = t.payment_id
WHERE p.order_id = :'order_id'
ORDER BY t.created_at;

\echo '===== [payment_db] 6. Outbox: event payment da phat ====='
SELECT event_type, status, retry_count, created_at, published_at
FROM outbox_events WHERE payload ->> 'orderId' = :'order_id'
ORDER BY created_at;

\c notification_db
\echo '===== [notification_db] 7. Thong bao da tao (moi event 1 dong, khong trung) ====='
SELECT type, content, created_at
FROM notifications WHERE order_id = :'order_id'
ORDER BY created_at;
