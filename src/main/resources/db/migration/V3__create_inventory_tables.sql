CREATE TABLE inventory_items (
    product_id       BIGINT      NOT NULL,
    quantity_on_hand INTEGER     NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_inventory_items PRIMARY KEY (product_id),
    CONSTRAINT fk_inventory_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventory_items_quantity_nonnegative CHECK (quantity_on_hand >= 0)
);

CREATE TABLE inventory_movements (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY,
    product_id           BIGINT       NOT NULL,
    quantity_change      INTEGER      NOT NULL,
    reason               VARCHAR(30)  NOT NULL,
    order_id             BIGINT,
    performed_by_user_id BIGINT       NOT NULL,
    note                 VARCHAR(500),
    created_at           TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_inventory_movements PRIMARY KEY (id),
    CONSTRAINT fk_inventory_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_movements_user FOREIGN KEY (performed_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_inventory_movements_quantity_change_nonzero CHECK (quantity_change <> 0),
    CONSTRAINT ck_inventory_movements_reason
        CHECK (reason IN ('RESTOCK', 'ADJUSTMENT', 'ORDER_PLACED', 'ORDER_CANCELLED'))
);

CREATE INDEX ix_inventory_movements_product_created_at ON inventory_movements (product_id, created_at DESC);
