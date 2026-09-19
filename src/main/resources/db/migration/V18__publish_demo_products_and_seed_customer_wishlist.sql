UPDATE products
SET publication_status='PUBLISHED',
    is_active=1,
    published_at=COALESCE(published_at,UTC_TIMESTAMP()),
    updated_at=UTC_TIMESTAMP()
WHERE sku IN ('MB-DEMO-DRS-001','MB-DEMO-JKT-002','MB-DEMO-TEE-003','MB-DEMO-BLZ-004')
  AND publication_status='DRAFT';

INSERT INTO wishlists(user_id,created_at)
SELECT u.id,UTC_TIMESTAMP()
FROM users u
WHERE LOWER(u.email)='gauti457@gmail.com'
  AND NOT EXISTS (SELECT 1 FROM wishlists w WHERE w.user_id=u.id);

INSERT INTO wishlist_items(wishlist_id,product_id,size,added_at)
SELECT w.id,p.id,NULL,UTC_TIMESTAMP()
FROM users u
JOIN wishlists w ON w.user_id=u.id
JOIN products p ON p.sku IN ('MB-DEMO-DRS-001','MB-DEMO-JKT-002')
WHERE LOWER(u.email)='gauti457@gmail.com'
  AND NOT EXISTS (
    SELECT 1 FROM wishlist_items wi
    WHERE wi.wishlist_id=w.id AND wi.product_id=p.id
  );

INSERT INTO coupons(code,description,type,discount_value,min_order_value,max_discount_amount,
    usage_limit_global,usage_limit_per_user,current_usage_count,is_active,is_public,audience_type,
    first_order_only,show_on_homepage,valid_from,valid_until,created_by,created_at,updated_at)
SELECT 'GAUTI500','A ₹500 thank-you offer selected for your account.','FLAT',500.00,249900,NULL,
       1,1,0,1,0,'ASSIGNED_USERS',0,0,UTC_TIMESTAMP(),UTC_TIMESTAMP()+INTERVAL 90 DAY,
       a.id,UTC_TIMESTAMP(),UTC_TIMESTAMP()
FROM admin_users a
WHERE a.is_active=1
  AND NOT EXISTS (SELECT 1 FROM coupons c WHERE UPPER(c.code)='GAUTI500')
ORDER BY a.id
LIMIT 1;

INSERT INTO coupon_user_assignments(coupon_id,user_id,usage_limit_override,is_active,
    assigned_reason,refund_id,assigned_by,assigned_at,updated_at)
SELECT c.id,u.id,NULL,1,'Profile coupon testing',NULL,a.id,UTC_TIMESTAMP(),UTC_TIMESTAMP()
FROM coupons c
JOIN users u ON LOWER(u.email)='gauti457@gmail.com'
JOIN admin_users a ON a.id=(SELECT MIN(id) FROM admin_users WHERE is_active=1)
WHERE UPPER(c.code)='GAUTI500'
  AND NOT EXISTS (
    SELECT 1 FROM coupon_user_assignments cua
    WHERE cua.coupon_id=c.id AND cua.user_id=u.id
  );
