-- Reset FLASH-001 truoc MOI lan chay flash sale (order_db).
--   Get-Content scripts\flashsale-reset.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db
-- order_items co ON DELETE CASCADE -> xoa orders la du. outbox_events cua cac don nay giu nguyen
-- (da gui hoac se gui; consumer idempotent).
DELETE FROM orders
WHERE id IN (SELECT order_id FROM order_items WHERE sku_code = 'FLASH-001');

UPDATE inventory SET available_qty = 10, reserved_qty = 0 WHERE sku_code = 'FLASH-001';
