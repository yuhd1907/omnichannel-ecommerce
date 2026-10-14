-- 1. Idempotency-Key cho POST /orders: client gui lai cung key (mat mang, bam 2 lan) thi nhan lai
--    DUNG don cu, khong tao don thu hai. Ghi cung transaction voi don hang.
--    PK (user_id, idem_key): key chi co nghia trong pham vi mot user.
CREATE TABLE idempotency_keys (
    user_id      UUID         NOT NULL,
    idem_key     VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64)  NOT NULL,   -- SHA-256 cua noi dung don: cung key khac noi dung -> 422
    order_id     UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, idem_key)
);

-- 2. Outbox relay chay nhieu instance: instance nhan lo bang FOR UPDATE SKIP LOCKED roi dat
--    locked_until; instance khac bo qua cac dong nay toi khi het han.
ALTER TABLE outbox_events ADD COLUMN locked_until TIMESTAMPTZ;
