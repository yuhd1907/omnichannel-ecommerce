-- Notification nhan them order.shipped / order.completed (vong doi giao hang) -> mo rong CHECK.
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type
    CHECK (type IN ('ORDER_CREATED', 'ORDER_CANCELLED', 'ORDER_CONFIRMED', 'ORDER_SHIPPED', 'ORDER_COMPLETED'));
