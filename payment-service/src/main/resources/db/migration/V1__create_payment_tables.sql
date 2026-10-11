-- V1__create_payment_tables.sql
-- Tao 3 bang cho payment_db: payments, payment_transactions, outbox_events

-- 1. Bang payments (phien thanh toan)
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    method VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    needs_refund BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0),
    CONSTRAINT chk_payments_method CHECK (method IS NULL OR method IN ('MOCK', 'VNPAY', 'MOMO')),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_payments_user_id ON payments (user_id);
CREATE INDEX idx_payments_needs_refund ON payments (needs_refund) WHERE needs_refund = true;

-- 2. Bang payment_transactions (lich su giao dich cong thanh toan)
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments (id),
    gateway_txn_id VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    raw_response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_transactions_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_payment_transactions_payment_id ON payment_transactions (payment_id);

-- 3. Bang outbox_events (Transactional Outbox pattern - giong order-service)
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
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);
