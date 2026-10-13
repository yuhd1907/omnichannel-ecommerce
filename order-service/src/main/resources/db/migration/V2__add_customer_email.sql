-- Snapshot email nguoi dat (lay tu JWT luc tao don) de notification gui mail cho moi event
-- cua don, ke ca event phat sinh khong co request (het han, ket qua thanh toan, admin giao hang).
-- Nullable: don tao truoc migration nay khong co email -> notification bo qua gui mail.
ALTER TABLE orders ADD COLUMN customer_email VARCHAR(255);
