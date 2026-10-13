-- SKU rieng cho load test thong luong (scripts/load-order-k6.js), tach khoi du lieu demo.
-- Ton kho cua SKU nay nam o order_db, dat bang scripts/load-reset.sql.
-- ON CONFLICT DO NOTHING: khong loi neu da tao tay qua API admin truoc do.
INSERT INTO products (id, name, slug, description, category_id, brand_id, base_price, status) VALUES
('c0000000-0000-0000-0000-0000000000f1', 'Load Test Item', 'load-test-item',
 'San pham ao dung cho load test, khong ban that.',
 'a0000000-0000-0000-0000-000000000022', 'b0000000-0000-0000-0000-000000000004', 10000.00, 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO product_skus (id, product_id, sku_code, attributes, price, image_url) VALUES
('d0000000-0000-0000-0000-0000000000f1', 'c0000000-0000-0000-0000-0000000000f1', 'LOAD-001',
 '{"note": "load-test"}'::jsonb, 10000.00, NULL)
ON CONFLICT DO NOTHING;
