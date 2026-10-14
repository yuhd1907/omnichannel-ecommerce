-- Tao 2 bang cho notification_db

-- 1. Bang notifications: moi thong bao (email gia lap) da gui cho user
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    order_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notifications_type CHECK (type IN ('ORDER_CREATED', 'ORDER_CANCELLED'))
);

CREATE INDEX idx_notifications_user_id_created_at ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_order_id ON notifications (order_id);

-- 2. Bang processed_messages: chong xu ly trung (outbox chi dam bao at-least-once).
-- message_id = AMQP messageId (= outbox_events.id ben phat). Kieu VARCHAR vi messageId
-- cua AMQP la chuoi; khong ep moi publisher phai dung UUID.
CREATE TABLE processed_messages (
    message_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
