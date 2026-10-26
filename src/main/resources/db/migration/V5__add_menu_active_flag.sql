
-- СИЕСТА: признак активности блюда
ALTER TABLE menu_dishes
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
