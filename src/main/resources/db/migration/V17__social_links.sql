CREATE TABLE social_links (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    platform_key VARCHAR(40) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    profile_url VARCHAR(2048) NOT NULL,
    icon_key VARCHAR(40) NOT NULL,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    updated_by INT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_social_links_platform (platform_key),
    CONSTRAINT fk_social_links_updated_by FOREIGN KEY (updated_by) REFERENCES admin_users(id) ON DELETE SET NULL
);

-- Test profiles copied from Myntra's current public social destinations.
INSERT INTO social_links(platform_key,display_name,profile_url,icon_key,display_order,is_enabled)
VALUES
    ('instagram','Instagram','https://www.instagram.com/myntra/','instagram',10,1),
    ('facebook','Facebook','https://www.facebook.com/myntra','facebook',20,1),
    ('x','X / Twitter','https://x.com/myntra','x',30,1),
    ('youtube','YouTube','https://www.youtube.com/@myntra','youtube',40,1),
    ('pinterest','Pinterest','https://in.pinterest.com/myntra/','pinterest',50,1),
    ('linkedin','LinkedIn','https://www.linkedin.com/company/myntra/','linkedin',60,1);
