CREATE TABLE site_content (
    id TINYINT UNSIGNED NOT NULL,
    about_title VARCHAR(160) NOT NULL,
    about_headline VARCHAR(300) NOT NULL,
    about_story TEXT NOT NULL,
    about_mission TEXT NOT NULL,
    contact_title VARCHAR(160) NOT NULL,
    contact_intro VARCHAR(500) NOT NULL,
    support_email VARCHAR(254) NOT NULL,
    support_phone VARCHAR(60) NOT NULL,
    support_hours VARCHAR(200) NOT NULL,
    registered_address TEXT NOT NULL,
    updated_by INT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_site_content_singleton CHECK (id = 1),
    CONSTRAINT fk_site_content_updated_by FOREIGN KEY (updated_by) REFERENCES admin_users(id) ON DELETE SET NULL
);

INSERT INTO site_content(
    id,about_title,about_headline,about_story,about_mission,
    contact_title,contact_intro,support_email,support_phone,support_hours,registered_address)
VALUES (
    1,
    'About MoodBuds',
    'Fashion that follows your feelings',
    'MoodBuds began with a simple idea — what you wear should match how you want to feel. Shop by mood, wear what you feel.',
    'To make getting dressed feel less like a chore and more like self-expression.',
    'Contact Us',
    'We are here to help — always.',
    'hello@moodbuds.com',
    '1800-MOOD-BUD',
    'Mon–Sat, 9am–8pm IST',
    'MoodBuds Retail Pvt. Ltd.\n4th Floor, Brigade Road,\nBengaluru 560001'
);

INSERT INTO admin_permissions(permission_key,module,description)
VALUES ('content.manage','content','Manage public About and Contact page content')
ON DUPLICATE KEY UPDATE module=VALUES(module),description=VALUES(description);

INSERT IGNORE INTO admin_role_permissions(role_id,permission_id)
SELECT r.id,p.id
FROM admin_roles r
JOIN admin_permissions p ON p.permission_key='content.manage'
WHERE r.name='SUPER_ADMIN';
