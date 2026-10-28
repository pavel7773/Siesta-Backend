ALTER TABLE account_verification_codes
DROP CONSTRAINT IF EXISTS account_verification_codes_purpose_check;

ALTER TABLE account_verification_codes
    ADD CONSTRAINT account_verification_codes_purpose_check
        CHECK (
            purpose IN (
                        'EMAIL_VERIFICATION',
                        'PASSWORD_RESET',
                        'PHONE_REGISTRATION'
                )
            );