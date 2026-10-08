ALTER TABLE orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX ix_orders_status_created_at ON orders (status, created_at DESC);
