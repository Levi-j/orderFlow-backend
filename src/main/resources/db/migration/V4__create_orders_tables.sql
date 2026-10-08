CREATE TABLE orders (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    customer_id  BIGINT         NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    total_amount NUMERIC(17, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount >= 0)
);

CREATE INDEX ix_orders_customer_created_at ON orders (customer_id, created_at DESC);

CREATE TABLE order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    product_sku  VARCHAR(64)    NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    line_total   NUMERIC(15, 2) NOT NULL,

    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 1000),
    CONSTRAINT ck_order_items_line_total CHECK (line_total = unit_price * quantity)
);

ALTER TABLE inventory_movements
    ADD CONSTRAINT fk_inventory_movements_order FOREIGN KEY (order_id) REFERENCES orders (id);
