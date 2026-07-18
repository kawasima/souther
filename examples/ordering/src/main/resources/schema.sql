CREATE TABLE IF NOT EXISTS orders (
    id      VARCHAR(64) PRIMARY KEY,
    item_id VARCHAR(64) NOT NULL,
    qty     INT         NOT NULL
);

CREATE TABLE IF NOT EXISTS stock (
    item_id VARCHAR(64) PRIMARY KEY,
    qty     INT         NOT NULL
);
