ALTER TABLE moods
    ADD COLUMN banner_image_path VARCHAR(500) NULL AFTER banner_image_url,
    ADD COLUMN banner_image_content_type VARCHAR(100) NULL AFTER banner_image_path,
    ADD COLUMN banner_image_size_bytes BIGINT UNSIGNED NULL AFTER banner_image_content_type,
    ADD COLUMN banner_image_width_px INT UNSIGNED NULL AFTER banner_image_size_bytes,
    ADD COLUMN banner_image_height_px INT UNSIGNED NULL AFTER banner_image_width_px,
    ADD COLUMN banner_image_updated_at DATETIME NULL AFTER banner_image_height_px;
