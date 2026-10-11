-- V2__optimize_needs_refund_partial_index.sql
-- Thay the partial index tren cot needs_refund bang index tren (created_at) voi WHERE needs_refund = true
-- giup endpoint doi soat vua loc duoc dong can hoan tien vua sap xep theo thoi gian tren index.

DROP INDEX IF EXISTS idx_payments_needs_refund;

CREATE INDEX idx_payments_needs_refund_created_at ON payments (created_at) WHERE needs_refund = true;
