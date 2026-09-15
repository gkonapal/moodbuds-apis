INSERT INTO categories(name, slug, is_active)
SELECT 'Clothing', 'demo-clothing', 1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug='demo-clothing');

INSERT INTO subcategories(category_id, name, slug, is_active)
SELECT c.id, 'Demo Collection', 'demo-collection', 1
FROM categories c
WHERE c.slug='demo-clothing'
  AND NOT EXISTS (SELECT 1 FROM subcategories WHERE slug='demo-collection');

INSERT INTO gst_rates(name, rate_percentage, hsn_code, is_active)
SELECT 'Demo Apparel GST', 12.00, '6204', 1
WHERE NOT EXISTS (SELECT 1 FROM gst_rates WHERE name='Demo Apparel GST' AND hsn_code='6204');

INSERT INTO products(
    sku,name,slug,category_id,subcategory_id,gst_rate_id,description,fabric_details,color_name,
    price,discount_price,weight_grams,is_featured,is_new_arrival,is_best_seller,return_window_days,
    is_active,created_by)
SELECT seed.sku,seed.name,seed.slug,c.id,sc.id,g.id,seed.description,seed.fabric,seed.color_name,
       seed.price,seed.discount_price,seed.weight_grams,seed.featured,seed.new_arrival,seed.best_seller,
       7,1,a.id
FROM (
    SELECT 'MB-DEMO-DRS-001' sku,'Sunlit Wrap Dress' name,'demo-sunlit-wrap-dress' slug,
           'A bright wrap dress from the MoodBuds demo collection.' description,'Viscose blend' fabric,
           'Sunflower Yellow' color_name,249900 price,219900 discount_price,420 weight_grams,
           1 featured,1 new_arrival,0 best_seller
    UNION ALL SELECT 'MB-DEMO-JKT-002','Midnight Denim Jacket','demo-midnight-denim-jacket',
           'A versatile denim layer from the MoodBuds demo collection.','Cotton denim','Midnight Blue',
           329900,NULL,780,0,0,1
    UNION ALL SELECT 'MB-DEMO-TEE-003','Calm Cotton Tee','demo-calm-cotton-tee',
           'A relaxed everyday tee from the MoodBuds demo collection.','Combed cotton','Cloud White',
           99900,84900,220,0,1,0
    UNION ALL SELECT 'MB-DEMO-BLZ-004','Executive Tailored Blazer','demo-executive-tailored-blazer',
           'A structured blazer from the MoodBuds demo collection.','Poly-viscose','Charcoal',
           499900,449900,850,1,0,1
) seed
JOIN categories c ON c.slug='demo-clothing'
JOIN subcategories sc ON sc.slug='demo-collection' AND sc.category_id=c.id
JOIN gst_rates g ON g.name='Demo Apparel GST' AND g.hsn_code='6204'
JOIN admin_users a ON a.id=(SELECT MIN(id) FROM admin_users WHERE is_active=1)
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.sku=seed.sku);

INSERT INTO product_sizes(product_id,size,stock_quantity,low_stock_threshold,is_available)
SELECT p.id,seed.size,seed.quantity,seed.threshold,seed.available
FROM (
    SELECT 'MB-DEMO-DRS-001' sku,'XS' size,8 quantity,4 threshold,1 available
    UNION ALL SELECT 'MB-DEMO-DRS-001','S',12,5,1
    UNION ALL SELECT 'MB-DEMO-DRS-001','M',4,5,1
    UNION ALL SELECT 'MB-DEMO-DRS-001','L',0,5,0
    UNION ALL SELECT 'MB-DEMO-JKT-002','S',6,3,1
    UNION ALL SELECT 'MB-DEMO-JKT-002','M',11,4,1
    UNION ALL SELECT 'MB-DEMO-JKT-002','L',9,4,1
    UNION ALL SELECT 'MB-DEMO-JKT-002','XL',3,4,1
    UNION ALL SELECT 'MB-DEMO-TEE-003','S',18,5,1
    UNION ALL SELECT 'MB-DEMO-TEE-003','M',25,6,1
    UNION ALL SELECT 'MB-DEMO-TEE-003','L',14,5,1
    UNION ALL SELECT 'MB-DEMO-TEE-003','XL',7,5,1
    UNION ALL SELECT 'MB-DEMO-BLZ-004','S',0,3,0
    UNION ALL SELECT 'MB-DEMO-BLZ-004','M',0,3,0
    UNION ALL SELECT 'MB-DEMO-BLZ-004','L',0,3,0
) seed
JOIN products p ON p.sku=seed.sku
WHERE NOT EXISTS (SELECT 1 FROM product_sizes ps WHERE ps.product_id=p.id AND ps.size=seed.size);

INSERT INTO product_images(product_id,image_url,is_primary,sort_order)
SELECT p.id,seed.image_url,1,0
FROM (
    SELECT 'MB-DEMO-DRS-001' sku,'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=480&q=80&fit=crop' image_url
    UNION ALL SELECT 'MB-DEMO-JKT-002','https://images.unsplash.com/photo-1544022613-e87ca75a784a?w=480&q=80&fit=crop'
    UNION ALL SELECT 'MB-DEMO-TEE-003','https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=480&q=80&fit=crop'
    UNION ALL SELECT 'MB-DEMO-BLZ-004','https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=480&q=80&fit=crop'
) seed
JOIN products p ON p.sku=seed.sku
WHERE NOT EXISTS (SELECT 1 FROM product_images pi WHERE pi.product_id=p.id);

INSERT INTO product_mood_tags(product_id,mood_id)
SELECT p.id,m.id
FROM (
    SELECT 'MB-DEMO-DRS-001' sku,'happy' mood_slug
    UNION ALL SELECT 'MB-DEMO-DRS-001','romantic'
    UNION ALL SELECT 'MB-DEMO-JKT-002','cool'
    UNION ALL SELECT 'MB-DEMO-TEE-003','calm'
    UNION ALL SELECT 'MB-DEMO-TEE-003','minimal'
    UNION ALL SELECT 'MB-DEMO-BLZ-004','confident'
    UNION ALL SELECT 'MB-DEMO-BLZ-004','professional'
) seed
JOIN products p ON p.sku=seed.sku
JOIN moods m ON m.slug=seed.mood_slug
WHERE NOT EXISTS (
    SELECT 1 FROM product_mood_tags pmt WHERE pmt.product_id=p.id AND pmt.mood_id=m.id
);
