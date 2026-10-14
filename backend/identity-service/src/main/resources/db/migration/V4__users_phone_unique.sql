-- V4: moi so dien thoai chi gan voi 1 tai khoan.
-- 1. Chuan hoa so da luu ve dang 0xxxxxxxxx (giong PhoneNumbers.normalize) de so trung dung du go khac kieu.
UPDATE users SET phone = NULLIF(regexp_replace(phone, '[^0-9]', '', 'g'), '') WHERE phone IS NOT NULL;
UPDATE users SET phone = '0' || substring(phone FROM 3) WHERE phone ~ '^84[0-9]{9}$';

-- 2. Du lieu cu co the da trung (truoc day khong kiem tra): giu so cho tai khoan tao som nhat,
--    xoa so (NULL) o cac tai khoan tao sau. Tai khoan van dang nhap binh thuong.
UPDATE users u SET phone = NULL
WHERE u.phone IS NOT NULL
  AND EXISTS (SELECT 1 FROM users o
              WHERE o.phone = u.phone
                AND (o.created_at, o.id) < (u.created_at, u.id));

-- 3. Unique tren cot phone; NULL (khong khai bao so) khong tinh trung.
CREATE UNIQUE INDEX uq_users_phone ON users (phone) WHERE phone IS NOT NULL;
