-- Outbox relay chay nhieu instance: nhan lo bang FOR UPDATE SKIP LOCKED roi dat locked_until;
-- instance khac bo qua cac dong nay toi khi het han (instance chet thi tu duoc nhan lai).
ALTER TABLE outbox_events ADD COLUMN locked_until TIMESTAMPTZ;
