ALTER TABLE admin_users
    ADD COLUMN username VARCHAR(100) NULL AFTER id,
    MODIFY COLUMN email VARCHAR(255) NULL,
    ADD CONSTRAINT uq_admin_users_username UNIQUE (username);

ALTER TABLE categories
    ADD COLUMN slug VARCHAR(140) NULL AFTER name,
    ADD CONSTRAINT uq_categories_slug UNIQUE (slug);

ALTER TABLE subcategories
    ADD COLUMN slug VARCHAR(140) NULL AFTER name,
    ADD CONSTRAINT uq_subcategories_slug UNIQUE (slug);

ALTER TABLE moods
    ADD COLUMN slug VARCHAR(140) NULL AFTER name,
    ADD CONSTRAINT uq_moods_slug UNIQUE (slug);

ALTER TABLE products
    ADD COLUMN slug VARCHAR(360) NULL AFTER name,
    ADD CONSTRAINT uq_products_slug UNIQUE (slug);

UPDATE moods
SET slug = LOWER(REPLACE(TRIM(name), ' ', '-'))
WHERE slug IS NULL;

ALTER TABLE moods MODIFY COLUMN slug VARCHAR(140) NOT NULL;
