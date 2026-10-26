
-- СИЕСТА
-- Одноразовые коды подтверждения email
-- и восстановления пароля через SMS/email.

CREATE TABLE account_verification_codes (
                                            id BIGSERIAL PRIMARY KEY,

                                            user_id INTEGER NOT NULL
                                                REFERENCES users(id) ON DELETE CASCADE,

                                            purpose VARCHAR(32) NOT NULL
                                                CHECK (
                                                    purpose IN (
                                                                'EMAIL_VERIFICATION',
                                                                'PASSWORD_RESET'
                                                        )
                                                    ),

                                            channel VARCHAR(16) NOT NULL
                                                CHECK (
                                                    channel IN ('EMAIL', 'SMS')
                                                    ),

                                            destination VARCHAR(254) NOT NULL,

    -- HMAC-SHA256 в шестнадцатеричном формате.
    -- Сам код здесь никогда не хранится.
                                            code_hash CHAR(64) NOT NULL,

                                            attempts_count INTEGER NOT NULL DEFAULT 0
                                                CHECK (attempts_count >= 0),

                                            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                            expires_at TIMESTAMPTZ NOT NULL,

                                            consumed_at TIMESTAMPTZ,

                                            CHECK (expires_at > created_at),

                                            CHECK (
                                                purpose <> 'EMAIL_VERIFICATION'
                                                    OR channel = 'EMAIL'
                                                )
);

-- Быстрый поиск актуального кода пользователя.
CREATE INDEX account_verification_codes_lookup_idx
    ON account_verification_codes (
                                   user_id,
                                   purpose,
                                   channel,
                                   created_at DESC
        )
    WHERE consumed_at IS NULL;
