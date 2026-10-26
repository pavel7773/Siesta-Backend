
-- СИЕСТА
-- Необязательная электронная почта пользователя

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email VARCHAR(254);

-- Время подтверждения адреса:
-- NULL означает, что email не подтверждён

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified_at BIGINT;

-- Один подтверждённый email
-- не может принадлежать двум аккаунтам.
-- Регистр букв не учитывается.

CREATE UNIQUE INDEX IF NOT EXISTS
    users_verified_email_unique_ci
    ON users (LOWER(email))
    WHERE email IS NOT NULL
    AND email_verified_at IS NOT NULL;
