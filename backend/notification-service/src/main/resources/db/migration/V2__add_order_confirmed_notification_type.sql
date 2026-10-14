-- Notification nhan them order.confirmed (don da thanh toan) -> mo rong CHECK cua cot type.
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type
    CHECK (type IN ('ORDER_CREATED', 'ORDER_CANCELLED', 'ORDER_CONFIRMED'));
