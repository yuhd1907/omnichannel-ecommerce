-- V4: them phuong thuc COD (thanh toan khi nhan hang).
-- COD khong qua cong: payment giu PENDING toi khi don COMPLETED (shipper thu tien) roi chuyen SUCCESS.
ALTER TABLE payments DROP CONSTRAINT chk_payments_method;
ALTER TABLE payments ADD CONSTRAINT chk_payments_method
    CHECK (method IS NULL OR method IN ('MOCK', 'VNPAY', 'MOMO', 'COD'));
