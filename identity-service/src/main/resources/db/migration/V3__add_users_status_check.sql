-- V3__add_users_status_check.sql
-- Bo sung rang buoc CHECK cho cot status cua bang users theo tai lieu DDD

ALTER TABLE users
ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'));
