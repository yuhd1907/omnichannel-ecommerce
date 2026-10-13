-- Kiem tra SAU load test (order_db). Truyen cung -v stock=... nhu luc reset (mac dinh 1000).
--   Get-Content scripts\load-verify.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db
-- DAT khi ca 4 cot cuoi deu la t (true).
\if :{?stock}
\else
  \set stock 1000
\endif

WITH qty AS (
    SELECT
        COALESCE(SUM(oi.quantity) FILTER (WHERE o.status = 'PENDING'), 0)                              AS pending_qty,
        COALESCE(SUM(oi.quantity) FILTER (WHERE o.status IN ('CONFIRMED', 'SHIPPING', 'COMPLETED')), 0) AS sold_qty,
        COALESCE(SUM(oi.quantity) FILTER (WHERE o.status = 'CANCELLED'), 0)                            AS cancelled_qty,
        COUNT(DISTINCT o.id)                                                                           AS orders
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    WHERE oi.sku_code = 'LOAD-001'
)
SELECT i.available_qty,
       i.reserved_qty,
       q.orders,
       q.pending_qty,
       q.sold_qty,
       q.cancelled_qty,
       -- 1. Khong am kho (CHECK constraint cung chan, kiem lai cho chac)
       (i.available_qty >= 0 AND i.reserved_qty >= 0)                    AS khong_am,
       -- 2. Hang dang giu dung bang tong SL cua don PENDING
       (i.reserved_qty = q.pending_qty)                                  AS giu_khop_pending,
       -- 3. Bao toan: con + dang giu + da ban = kho ban dau
       (i.available_qty + i.reserved_qty + q.sold_qty = :stock)          AS bao_toan_kho,
       -- 4. Khong ban vuot: tong SL don chua huy <= kho ban dau
       (q.pending_qty + q.sold_qty <= :stock)                            AS khong_ban_vuot
FROM inventory i, qty q
WHERE i.sku_code = 'LOAD-001';
