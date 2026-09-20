SET @add_product_color_hex = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE products ADD COLUMN color_hex CHAR(7) NULL AFTER color_name',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'products'
      AND column_name = 'color_hex'
);
PREPARE add_product_color_hex_statement FROM @add_product_color_hex;
EXECUTE add_product_color_hex_statement;
DEALLOCATE PREPARE add_product_color_hex_statement;

UPDATE products SET color_hex = '#FFFFFF'
WHERE LOWER(TRIM(color_name)) = 'white';

UPDATE products SET color_hex = '#F5F5F0'
WHERE LOWER(TRIM(color_name)) = 'cloud white';

UPDATE products SET color_hex = '#36454F'
WHERE LOWER(TRIM(color_name)) = 'charcoal';

UPDATE products SET color_hex = '#1F2A44'
WHERE LOWER(TRIM(color_name)) = 'midnight blue';

UPDATE products SET color_hex = '#E8B923'
WHERE LOWER(TRIM(color_name)) = 'sunflower yellow';
