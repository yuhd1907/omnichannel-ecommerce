-- V6: anh san pham.
-- 1. Anh dai dien cua san pham (hien o danh sach/trang chu). Tu lay tu anh SKU dau tien duoc tai len.
ALTER TABLE products ADD COLUMN image_url TEXT;

-- 2. Du lieu mau V2 tro toi cdn.example.com (ten mien khong ton tai) -> anh hong tren web.
--    Bo di de giao dien hien anh thay the; admin tai anh that len o trang Quan tri.
UPDATE product_skus SET image_url = NULL WHERE image_url LIKE 'https://cdn.example.com/%';
