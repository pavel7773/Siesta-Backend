ALTER TABLE users
    ADD COLUMN phone_verified_at BIGINT NULL;

UPDATE users
SET phone_verified_at =
        (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
WHERE phone_verified_at IS NULL;