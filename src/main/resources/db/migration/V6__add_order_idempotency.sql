ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN request_hash    CHAR(64),
    ADD CONSTRAINT uk_orders_customer_idempotency_key UNIQUE (customer_id, idempotency_key);
