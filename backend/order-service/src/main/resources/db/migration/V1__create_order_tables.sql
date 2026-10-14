-- V1__create_order_tables.sql
-- Tao 4 bang theo ERD cho order_db: inventory, orders, order_items, outbox_events

-- 1. Bang inventory (quan ly ton kho theo sku_code)
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku_code VARCHAR(100) NOT NULL,
    available_qty BIGINT NOT NULL DEFAULT 0,
    reserved_qty BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_sku_code UNIQUE (sku_code),
    CONSTRAINT chk_inventory_qty CHECK (available_qty >= 0 AND reserved_qty >= 0)
);

-- 2. Bang orders (don hang)
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_status VARCHAR(50) NOT NULL DEFAULT 'UNPAID',
    total_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    shipping_recipient_name VARCHAR(255) NOT NULL,
    shipping_phone VARCHAR(50) NOT NULL,
    shipping_address TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_orders_channel CHECK (channel IN ('WEB', 'MOBILE')),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_orders_payment_status CHECK (payment_status IN ('UNPAID', 'PAID', 'FAILED')),
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0)
);

-- Index phuc vu tra cuu danh sach don cua user theo thoi gian moi nhat
CREATE INDEX idx_orders_user_created_at ON orders (user_id, created_at DESC);

-- Index phuc vu job timeout tim don PENDING qua han
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);

-- 3. Bang order_items (chi tiet mat hang trong don)
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    sku_code VARCHAR(100) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    quantity BIGINT NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_items_subtotal CHECK (subtotal >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- 4. Bang outbox_events (Transactional Outbox pattern)
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
