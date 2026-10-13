-- Reset LOAD-001 truoc MOI lan chay load test (order_db).
--   Get-Content scripts\load-reset.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db
-- Doi so luong kho (mac dinh 1000):
--   Get-Content scripts\load-reset.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db -v stock=500
\if :{?stock}
\else
  \set stock 1000
\endif

-- order_items ON DELETE CASCADE -> xoa orders la du.
DELETE FROM orders
WHERE id IN (SELECT order_id FROM order_items WHERE sku_code = 'LOAD-001');

-- Upsert: lan dau chua co dong inventory thi tao moi.
INSERT INTO inventory (sku_code, available_qty, reserved_qty)
VALUES ('LOAD-001', :stock, 0)
ON CONFLICT (sku_code) DO UPDATE
SET available_qty = EXCLUDED.available_qty,
    reserved_qty  = 0,
    version       = inventory.version + 1;

SELECT sku_code, available_qty, reserved_qty FROM inventory WHERE sku_code = 'LOAD-001';
