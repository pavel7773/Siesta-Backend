
-- СИЕСТА: переключение на новое меню.
-- Сохраняем старые блюда для истории заказов.

DO $$
DECLARE
siesta_count INTEGER;
    legacy_count INTEGER;
    total_count INTEGER;
BEGIN
SELECT COUNT(*)
INTO siesta_count
FROM menu_dishes
WHERE image_url LIKE 'siesta_%'
  AND is_active = FALSE;

SELECT COUNT(*)
INTO legacy_count
FROM menu_dishes
WHERE image_url NOT LIKE 'siesta_%'
  AND is_active = TRUE;

SELECT COUNT(*)
INTO total_count
FROM menu_dishes;

-- Защита от переключения неполного каталога
IF siesta_count <> 69
       OR legacy_count <> 6
       OR total_count <> 75 THEN
        RAISE EXCEPTION
            'Неожиданное количество блюд: СИЕСТА %, старые %, всего %',
            siesta_count,
            legacy_count,
            total_count;
END IF;

    -- Активируем новое меню
UPDATE menu_dishes
SET is_active = TRUE
WHERE image_url LIKE 'siesta_%';

-- Скрываем старое меню
UPDATE menu_dishes
SET is_active = FALSE
WHERE image_url NOT LIKE 'siesta_%';
END
$$;
