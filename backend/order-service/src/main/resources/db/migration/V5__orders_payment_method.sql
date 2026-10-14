-- V5: luu phuong thuc thanh toan cua don (COD / VNPAY / MOCK) de khach va admin biet don nao
-- phai thu tien mat khi giao. NULL = chua chon (don PENDING) hoac don cu truoc migration nay.
ALTER TABLE orders ADD COLUMN payment_method VARCHAR(20);
ALTER TABLE orders ADD CONSTRAINT chk_orders_payment_method
    CHECK (payment_method IS NULL OR payment_method IN ('COD', 'VNPAY', 'MOCK', 'MOMO'));
