-- Transactional Outbox cho product-service (giong order-service / payment-service).
-- Tao SKU va ghi event PRODUCT_SKU_CREATED trong CUNG transaction; OutboxRelay publish len
-- exchange product.events -> order-service tu tao dong kho (available_qty = 0) cho SKU moi.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    -- Relay chay nhieu instance: dong da duoc mot instance nhan thi cac instance khac bo qua
    -- den het han (FOR UPDATE SKIP LOCKED + locked_until).
    locked_until TIMESTAMPTZ,
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);
