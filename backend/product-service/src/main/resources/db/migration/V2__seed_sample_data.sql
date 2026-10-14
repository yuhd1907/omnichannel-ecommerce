-- V2__seed_sample_data.sql
-- Seed du lieu mau cho product_db de test phan trang va phuc vu Order service goi sang

-- 1. Seed Categories (Co ca root category va child category theo self-FK parent_id)
-- Root categories
INSERT INTO categories (id, name, slug, parent_id) VALUES
('a0000000-0000-0000-0000-000000000001', 'Thiết bị điện tử', 'thiet-bi-dien-tu', NULL),
('a0000000-0000-0000-0000-000000000002', 'Thời trang', 'thoi-trang', NULL);

-- Sub categories
INSERT INTO categories (id, name, slug, parent_id) VALUES
('a0000000-0000-0000-0000-000000000011', 'Điện thoại thông minh', 'dien-thoai-thong-minh', 'a0000000-0000-0000-0000-000000000001'),
('a0000000-0000-0000-0000-000000000012', 'Laptop', 'laptop', 'a0000000-0000-0000-0000-000000000001'),
('a0000000-0000-0000-0000-000000000013', 'Phụ kiện âm thanh', 'phu-kien-am-thanh', 'a0000000-0000-0000-0000-000000000001'),
('a0000000-0000-0000-0000-000000000021', 'Giày dép', 'giay-dep', 'a0000000-0000-0000-0000-000000000002'),
('a0000000-0000-0000-0000-000000000022', 'Quần áo thể thao', 'quan-ao-the-thao', 'a0000000-0000-0000-0000-000000000002');

-- 2. Seed Brands
INSERT INTO brands (id, name, slug) VALUES
('b0000000-0000-0000-0000-000000000001', 'Apple', 'apple'),
('b0000000-0000-0000-0000-000000000002', 'Samsung', 'samsung'),
('b0000000-0000-0000-0000-000000000003', 'Sony', 'sony'),
('b0000000-0000-0000-0000-000000000004', 'Nike', 'nike'),
('b0000000-0000-0000-0000-000000000005', 'ASUS', 'asus');

-- 3. Seed Products (8 san pham mau, status ACTIVE)
INSERT INTO products (id, name, slug, description, category_id, brand_id, base_price, status) VALUES
('c0000000-0000-0000-0000-000000000001', 'iPhone 15 Pro Max', 'iphone-15-pro-max', 'Flagship cao cấp của Apple với khung viền Titan và chip A17 Pro mạnh mẽ.', 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000001', 29990000.00, 'ACTIVE'),
('c0000000-0000-0000-0000-000000000002', 'iPhone 15', 'iphone-15', 'Màn hình Dynamic Island, camera 48MP và cổng kết nối USB-C tiện lợi.', 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000001', 19990000.00, 'ACTIVE'),
('c0000000-0000-0000-0000-000000000003', 'Samsung Galaxy S24 Ultra', 'samsung-galaxy-s24-ultra', 'Trải nghiệm đỉnh cao công nghệ cùng Galaxy AI và bút S-Pen tích hợp.', 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000002', 27990000.00, 'ACTIVE'),
('c0000000-0000-0000-0000-000000000004', 'Samsung Galaxy Z Flip 5', 'samsung-galaxy-z-flip-5', 'Điện thoại màn hình gập thời trang, camera sắc nét và bản lề Flex cải tiến.', 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000002', 18990000.00, 'ACTIVE'),
('c0000000-0000-0000-0000-000000000005', 'MacBook Air M2 13 inch', 'macbook-air-m2-13-inch', 'Thiết kế mỏng nhẹ đỉnh cao, hiệu năng vượt trội với chip Apple M2.', 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000001', 24990000.00, 'ACTIVE'),
('c0000000-0000-0000-0000-000000000006', 'ASUS ROG Zephyrus G14', 'asus-rog-zephyrus-g14', 'Laptop gaming mỏng nhẹ, màn hình OLED và card đồ họa NVIDIA RTX.', 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000005', 35990000.00, 'ACTIVE'),
('c0000000-0000-0000-0000-000000000007', 'Sony WH-1000XM5', 'sony-wh-1000xm5', 'Tai nghe chống ồn không dây hàng đầu với chất lượng âm thanh Hi-Res tuyệt hảo.', 'a0000000-0000-0000-0000-000000000013', 'b0000000-0000-0000-0000-000000000003', 7490000.00, 'ACTIVE'),
('c0000000-0000-0000-0000-000000000008', 'Nike Air Force 1 07', 'nike-air-force-1-07', 'Mẫu giày sneaker kinh điển, phong cách thể thao năng động và bền bỉ.', 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000004', 2929000.00, 'ACTIVE');

-- 4. Seed Product SKUs (15 SKUs voi cac bien the va ma sku_code thuc te cho Order/Inventory)
INSERT INTO product_skus (id, product_id, sku_code, attributes, price, image_url) VALUES
-- iPhone 15 Pro Max SKUs
('d0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'IPHONE15PM-NAT-256', '{"color": "Titan Tự Nhiên", "storage": "256GB"}'::jsonb, 29990000.00, 'https://cdn.example.com/products/iphone15pm-nat.jpg'),
('d0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'IPHONE15PM-BLK-512', '{"color": "Titan Đen", "storage": "512GB"}'::jsonb, 34990000.00, 'https://cdn.example.com/products/iphone15pm-blk.jpg'),

-- iPhone 15 SKUs
('d0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000002', 'IPHONE15-BLU-128', '{"color": "Xanh Dương", "storage": "128GB"}'::jsonb, 19990000.00, 'https://cdn.example.com/products/iphone15-blu.jpg'),
('d0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000002', 'IPHONE15-PNK-128', '{"color": "Hồng", "storage": "128GB"}'::jsonb, 19990000.00, 'https://cdn.example.com/products/iphone15-pnk.jpg'),

-- Samsung Galaxy S24 Ultra SKUs
('d0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000003', 'S24U-GRY-256', '{"color": "Xám Titan", "storage": "256GB"}'::jsonb, 27990000.00, 'https://cdn.example.com/products/s24u-gry.jpg'),
('d0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000003', 'S24U-BLK-512', '{"color": "Đen Titan", "storage": "512GB"}'::jsonb, 31990000.00, 'https://cdn.example.com/products/s24u-blk.jpg'),

-- Samsung Galaxy Z Flip 5 SKUs
('d0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000004', 'ZFLIP5-MNT-256', '{"color": "Xanh Mint", "storage": "256GB"}'::jsonb, 18990000.00, 'https://cdn.example.com/products/zflip5-mnt.jpg'),

-- MacBook Air M2 SKUs
('d0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000005', 'MBA-M2-SLV-256', '{"color": "Bạc", "ram": "8GB", "storage": "256GB"}'::jsonb, 24990000.00, 'https://cdn.example.com/products/mba-m2-slv.jpg'),
('d0000000-0000-0000-0000-000000000009', 'c0000000-0000-0000-0000-000000000005', 'MBA-M2-SGY-512', '{"color": "Xám Không Gian", "ram": "16GB", "storage": "512GB"}'::jsonb, 30990000.00, 'https://cdn.example.com/products/mba-m2-sgy.jpg'),

-- ASUS ROG Zephyrus G14 SKUs
('d0000000-0000-0000-0000-000000000010', 'c0000000-0000-0000-0000-000000000006', 'ROG-G14-WHT-1TB', '{"color": "Trắng", "ram": "16GB", "storage": "1TB"}'::jsonb, 35990000.00, 'https://cdn.example.com/products/rog-g14-wht.jpg'),

-- Sony WH-1000XM5 SKUs
('d0000000-0000-0000-0000-000000000011', 'c0000000-0000-0000-0000-000000000007', 'SONY-XM5-BLK', '{"color": "Đen"}'::jsonb, 7490000.00, 'https://cdn.example.com/products/sony-xm5-blk.jpg'),
('d0000000-0000-0000-0000-000000000012', 'c0000000-0000-0000-0000-000000000007', 'SONY-XM5-SLV', '{"color": "Bạc"}'::jsonb, 7490000.00, 'https://cdn.example.com/products/sony-xm5-slv.jpg'),

-- Nike Air Force 1 SKUs
('d0000000-0000-0000-0000-000000000013', 'c0000000-0000-0000-0000-000000000008', 'NIKE-AF1-WHT-40', '{"color": "Trắng", "size": "40"}'::jsonb, 2929000.00, 'https://cdn.example.com/products/nike-af1-wht.jpg'),
('d0000000-0000-0000-0000-000000000014', 'c0000000-0000-0000-0000-000000000008', 'NIKE-AF1-WHT-41', '{"color": "Trắng", "size": "41"}'::jsonb, 2929000.00, 'https://cdn.example.com/products/nike-af1-wht.jpg'),
('d0000000-0000-0000-0000-000000000015', 'c0000000-0000-0000-0000-000000000008', 'NIKE-AF1-WHT-42', '{"color": "Trắng", "size": "42"}'::jsonb, 2929000.00, 'https://cdn.example.com/products/nike-af1-wht.jpg');
