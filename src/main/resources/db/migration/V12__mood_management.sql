ALTER TABLE moods
    ADD COLUMN emoji VARCHAR(20) NULL AFTER slug,
    ADD COLUMN display_order INT UNSIGNED NOT NULL DEFAULT 0 AFTER color;

UPDATE moods SET emoji='😊', display_order=1 WHERE slug='happy';
UPDATE moods SET emoji='🦁', display_order=2 WHERE slug='confident';
UPDATE moods SET emoji='😎', display_order=3 WHERE slug='cool';
UPDATE moods SET emoji='💼', display_order=4 WHERE slug='professional';
UPDATE moods SET emoji='🎉', display_order=5 WHERE slug='party';
UPDATE moods SET emoji='⚡', display_order=6 WHERE slug='energetic';
UPDATE moods SET emoji='🌹', display_order=7 WHERE slug='romantic';
UPDATE moods SET emoji='🌊', display_order=8 WHERE slug='calm';
UPDATE moods SET emoji='🖤', display_order=9 WHERE slug='minimal';

UPDATE moods SET display_order=id WHERE display_order=0;

CREATE INDEX idx_moods_active_order ON moods(is_active, display_order, id);
