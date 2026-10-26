CREATE TABLE users
(
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    address       VARCHAR(255) NOT NULL DEFAULT '',
    CONSTRAINT users_phone_unique UNIQUE (phone)
);

CREATE TABLE menu_dishes
(
    id        SERIAL PRIMARY KEY,
    name      VARCHAR(100)     NOT NULL,
    price     INTEGER          NOT NULL,
    info      VARCHAR(500)     NOT NULL DEFAULT '',
    image_url VARCHAR(1000)    NOT NULL DEFAULT '',
    weight    INTEGER          NOT NULL DEFAULT 0,
    calories  INTEGER          NOT NULL DEFAULT 0,
    proteins  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    fats      DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    carbs     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    category  VARCHAR(100)     NOT NULL DEFAULT ''
);

CREATE TABLE orders
(
    id            SERIAL PRIMARY KEY,
    user_id       INTEGER      NOT NULL,
    name          VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    comment       VARCHAR(500) NOT NULL,
    delivery_type VARCHAR(20)  NOT NULL,
    address       VARCHAR(300) NOT NULL,
    total_amount  BIGINT       NOT NULL,
    created_at    BIGINT       NOT NULL,
    status_order  VARCHAR(20)  NOT NULL,

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE RESTRICT
);

CREATE TABLE order_items
(
    id       SERIAL PRIMARY KEY,
    order_id INTEGER      NOT NULL,
    dish_id  INTEGER      NOT NULL,
    name     VARCHAR(100) NOT NULL,
    quantity INTEGER      NOT NULL,
    price    INTEGER      NOT NULL,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE CASCADE
);

SELECT
    installed_rank,
    version,
    description,
    type,
    success
FROM flyway_schema_history
ORDER BY installed_rank;