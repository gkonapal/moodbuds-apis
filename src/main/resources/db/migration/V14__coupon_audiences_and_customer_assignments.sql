ALTER TABLE coupons
    ADD COLUMN audience_type ENUM('PUBLIC','PRIVATE_CODE','ASSIGNED_USERS') NOT NULL DEFAULT 'PUBLIC' AFTER is_public,
    ADD COLUMN first_order_only TINYINT(1) NOT NULL DEFAULT 0 AFTER audience_type,
    ADD COLUMN show_on_homepage TINYINT(1) NOT NULL DEFAULT 0 AFTER first_order_only,
    ADD INDEX idx_coupons_audience_home (audience_type,show_on_homepage,is_active);

UPDATE coupons
SET audience_type = CASE WHEN is_public = 1 THEN 'PUBLIC' ELSE 'PRIVATE_CODE' END;

CREATE TABLE coupon_user_assignments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    usage_limit_override INT UNSIGNED NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    assigned_reason VARCHAR(255) NULL,
    refund_id BIGINT UNSIGNED NULL,
    assigned_by INT UNSIGNED NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_coupon_user_assignment (coupon_id,user_id),
    KEY idx_coupon_assignments_user_active (user_id,is_active),
    KEY idx_coupon_assignments_coupon_active (coupon_id,is_active),
    KEY idx_coupon_assignments_refund (refund_id),
    CONSTRAINT fk_coupon_assignments_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE,
    CONSTRAINT fk_coupon_assignments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_coupon_assignments_refund FOREIGN KEY (refund_id) REFERENCES refunds(id) ON DELETE SET NULL,
    CONSTRAINT fk_coupon_assignments_admin FOREIGN KEY (assigned_by) REFERENCES admin_users(id)
);

ALTER TABLE orders ADD INDEX idx_orders_user_status (user_id,status);

UPDATE coupons SET show_on_homepage = 0;

INSERT INTO coupons(code,description,type,discount_value,min_order_value,max_discount_amount,
    usage_limit_global,usage_limit_per_user,current_usage_count,is_active,is_public,audience_type,
    first_order_only,show_on_homepage,valid_from,valid_until,created_by,created_at,updated_at)
SELECT 'MOOD300','₹300 off on your first order above ₹1,499.','FLAT',300.00,149900,NULL,
       NULL,1,0,1,1,'PUBLIC',1,1,UTC_TIMESTAMP(),NULL,a.id,UTC_TIMESTAMP(),UTC_TIMESTAMP()
FROM admin_users a
WHERE NOT EXISTS (SELECT 1 FROM coupons c WHERE UPPER(c.code)='MOOD300')
ORDER BY a.id LIMIT 1;

UPDATE coupons SET audience_type='PUBLIC',is_public=1,first_order_only=1,
    show_on_homepage=1,usage_limit_per_user=1
WHERE UPPER(code)='MOOD300';
