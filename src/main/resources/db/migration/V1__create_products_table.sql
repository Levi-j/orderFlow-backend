CREATE TABLE products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    sku         VARCHAR(64)   NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    price       NUMERIC(12, 2) NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_sku_format CHECK (sku ~ '^[A-Z0-9-]+$'),
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);
