CREATE TABLE media_assets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    width_px INT UNSIGNED NULL,
    height_px INT UNSIGNED NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    created_by INT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_media_assets_storage_key (storage_key),
    KEY idx_media_assets_created_by (created_by),
    CONSTRAINT fk_media_assets_created_by FOREIGN KEY (created_by) REFERENCES admin_users(id)
);

ALTER TABLE products
    ADD COLUMN publication_status ENUM('DRAFT','PUBLISHED','ARCHIVED') NOT NULL DEFAULT 'DRAFT' AFTER is_active,
    ADD COLUMN published_at DATETIME NULL AFTER publication_status,
    ADD COLUMN updated_by INT UNSIGNED NULL AFTER created_by,
    ADD KEY idx_products_publication_status (publication_status),
    ADD CONSTRAINT fk_products_updated_by FOREIGN KEY (updated_by) REFERENCES admin_users(id);

UPDATE products
SET publication_status = IF(is_active = 1, 'PUBLISHED', 'DRAFT'),
    published_at = IF(is_active = 1, COALESCE(updated_at, created_at), NULL);

ALTER TABLE products ALTER COLUMN is_active SET DEFAULT 0;

ALTER TABLE product_images
    ADD COLUMN media_asset_id BIGINT UNSIGNED NULL AFTER product_id,
    ADD COLUMN sort_order INT UNSIGNED NOT NULL DEFAULT 0 AFTER is_primary,
    ADD KEY idx_product_images_media (media_asset_id),
    ADD CONSTRAINT fk_product_images_media FOREIGN KEY (media_asset_id) REFERENCES media_assets(id);

DELETE older FROM size_charts older
JOIN size_charts newer ON newer.product_id = older.product_id AND newer.id > older.id
WHERE older.product_id IS NOT NULL;

DELETE older FROM size_charts older
JOIN size_charts newer ON newer.subcategory_id = older.subcategory_id AND newer.id > older.id
WHERE older.product_id IS NULL AND newer.product_id IS NULL AND older.subcategory_id IS NOT NULL;

ALTER TABLE size_charts
    ADD COLUMN media_asset_id BIGINT UNSIGNED NULL AFTER product_id,
    ADD KEY idx_size_charts_media (media_asset_id),
    ADD CONSTRAINT fk_size_charts_media FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
    ADD UNIQUE KEY uq_size_charts_product (product_id),
    ADD UNIQUE KEY uq_size_charts_subcategory (subcategory_id);
