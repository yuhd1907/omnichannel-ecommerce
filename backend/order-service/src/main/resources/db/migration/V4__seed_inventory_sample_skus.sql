-- Kho mac dinh cho cac SKU mau (product-service V2__seed_sample_data.sql) de dat hang duoc
-- ngay sau 'docker compose up', khong can admin nhap kho truoc.
-- SKU tao moi qua API thi tu co dong kho (available = 0) nho event product.sku.created.
-- ON CONFLICT DO NOTHING: database da co dong kho (VPS, may da chay Postman) thi giu nguyen so cu.
INSERT INTO inventory (sku_code, available_qty, reserved_qty, version)
VALUES ('IPHONE15PM-NAT-256', 50, 0, 0),
       ('IPHONE15PM-BLK-512', 50, 0, 0),
       ('IPHONE15-BLU-128',   50, 0, 0),
       ('IPHONE15-PNK-128',   50, 0, 0),
       ('S24U-GRY-256',       50, 0, 0),
       ('S24U-BLK-512',       50, 0, 0),
       ('ZFLIP5-MNT-256',     50, 0, 0),
       ('MBA-M2-SLV-256',     50, 0, 0),
       ('MBA-M2-SGY-512',     50, 0, 0),
       ('ROG-G14-WHT-1TB',    50, 0, 0),
       ('SONY-XM5-BLK',       50, 0, 0),
       ('SONY-XM5-SLV',       50, 0, 0),
       ('NIKE-AF1-WHT-40',    50, 0, 0),
       ('NIKE-AF1-WHT-41',    50, 0, 0),
       ('NIKE-AF1-WHT-42',    50, 0, 0)
ON CONFLICT (sku_code) DO NOTHING;
