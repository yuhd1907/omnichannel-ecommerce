-- Kiem tra SAU moi lan chay flash sale (order_db).
--   Get-Content scripts\flashsale-verify.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db
-- Dat khi: 0 | 10  va  10 | 10.
SELECT available_qty, reserved_qty FROM inventory WHERE sku_code = 'FLASH-001';

SELECT COUNT(*) AS orders, SUM(oi.quantity) AS qty
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE oi.sku_code = 'FLASH-001';
