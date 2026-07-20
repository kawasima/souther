CREATE TABLE IF NOT EXISTS orders (
    id    VARCHAR(64) PRIMARY KEY,
    total INT         NOT NULL
);

CREATE TABLE IF NOT EXISTS order_lines (
    order_id   VARCHAR(64) NOT NULL,
    sku        VARCHAR(64) NOT NULL,
    qty        INT         NOT NULL,
    unit_price INT         NOT NULL
);

CREATE TABLE IF NOT EXISTS stock (
    sku VARCHAR(64) PRIMARY KEY,
    qty INT         NOT NULL
);
