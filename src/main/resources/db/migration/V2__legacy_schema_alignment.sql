-- 1. Нормализация старых телефонных номеров

WITH normalized AS (SELECT id,
                           regexp_replace(phone, '[^0-9]', '', 'g') AS digits
                    FROM users)
UPDATE users AS u
SET phone =
        CASE
            WHEN n.digits ~ '^8[0-9]{10}$'
                THEN '7' || substring(n.digits FROM 2)

            WHEN n.digits ~ '^7[0-9]{10}$'
                THEN n.digits

            ELSE u.phone
            END
FROM normalized AS n
WHERE u.id = n.id
  AND n.digits ~ '^[78][0-9]{10}$';


-- 2. Старые базы могли иметь total_amount INTEGER.
--    Приводим колонку к BIGINT только если это ещё не BIGINT.

DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_name = 'orders'
                     AND column_name = 'total_amount'
                     AND data_type <> 'bigint') THEN
            ALTER TABLE orders
                ALTER COLUMN total_amount
                    TYPE BIGINT
                    USING total_amount::BIGINT;
        END IF;
    END
$$;


-- 3. Foreign key orders.user_id -> users.id

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM pg_constraint
                       WHERE conname = 'fk_orders_user'
                         AND conrelid = 'orders'::regclass) THEN
            ALTER TABLE orders
                ADD CONSTRAINT fk_orders_user
                    FOREIGN KEY (user_id)
                        REFERENCES users (id)
                        ON DELETE RESTRICT;
        END IF;
    END
$$;


-- 4. Foreign key order_items.order_id -> orders.id

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM pg_constraint
                       WHERE conname = 'fk_order_items_order'
                         AND conrelid = 'order_items'::regclass) THEN
            ALTER TABLE order_items
                ADD CONSTRAINT fk_order_items_order
                    FOREIGN KEY (order_id)
                        REFERENCES orders (id)
                        ON DELETE CASCADE;
        END IF;
    END
$$;