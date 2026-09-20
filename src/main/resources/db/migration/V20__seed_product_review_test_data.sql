-- Explicit local test data for exercising product-review display and pagination.
-- These reviewer identities have no password and cannot be used to sign in.
INSERT INTO users (email, password_hash, first_name, last_name, email_verified, is_active)
VALUES
    ('aisha.review@moodbuds.test', NULL, 'Aisha', 'Reviewer', 1, 1),
    ('ben.review@moodbuds.test', NULL, 'Ben', 'Reviewer', 1, 1),
    ('charu.review@moodbuds.test', NULL, 'Charu', 'Reviewer', 1, 1),
    ('dev.review@moodbuds.test', NULL, 'Dev', 'Reviewer', 1, 1),
    ('esha.review@moodbuds.test', NULL, 'Esha', 'Reviewer', 1, 1),
    ('farhan.review@moodbuds.test', NULL, 'Farhan', 'Reviewer', 1, 1),
    ('isha.review@moodbuds.test', NULL, 'Isha', 'Reviewer', 1, 1),
    ('jai.review@moodbuds.test', NULL, 'Jai', 'Reviewer', 1, 1)
ON DUPLICATE KEY UPDATE email = VALUES(email);

-- Give every currently published product one review from the requested customer.
INSERT INTO product_reviews
    (product_id, user_id, rating, title, comment, is_verified_purchase, helpful_count, created_at, updated_at)
SELECT p.id, u.id, 5, 'Really happy with this purchase',
       CONCAT('The ', p.name, ' looks great, feels comfortable and matches the product description.'),
       1, 3, DATE_SUB(UTC_TIMESTAMP(), INTERVAL p.id HOUR), DATE_SUB(UTC_TIMESTAMP(), INTERVAL p.id HOUR)
FROM products p
JOIN users u ON u.email = 'gauti457@gmail.com' AND u.is_active = 1
LEFT JOIN product_reviews existing ON existing.product_id = p.id AND existing.user_id = u.id
WHERE p.is_active = 1
  AND p.publication_status = 'PUBLISHED'
  AND existing.id IS NULL;

-- Add nine more distinct reviewers to the first published product. Together with
-- the requested customer's review above, this product has exactly ten reviews.
INSERT INTO product_reviews
    (product_id, user_id, rating, title, comment, is_verified_purchase, helpful_count, created_at, updated_at)
SELECT target.id, reviewer.id, seed.rating, seed.title, seed.comment,
       seed.verified, seed.helpful_count,
       DATE_SUB(UTC_TIMESTAMP(), INTERVAL seed.age_hours HOUR),
       DATE_SUB(UTC_TIMESTAMP(), INTERVAL seed.age_hours HOUR)
FROM (
    SELECT 'gauti@gmail.com' email, 4 rating, 'Comfortable and stylish' title,
           'The fit is comfortable and the styling works well for both casual and dressy occasions.' comment,
           1 verified, 6 helpful_count, 2 age_hours
    UNION ALL SELECT 'aisha.review@moodbuds.test', 5, 'Exactly as pictured',
           'The colour and finish match the photos. The fabric also feels better than expected.', 1, 5, 4
    UNION ALL SELECT 'ben.review@moodbuds.test', 4, 'Good quality for the price',
           'A well-made product with neat stitching. The sizing guide was accurate for me.', 1, 3, 6
    UNION ALL SELECT 'charu.review@moodbuds.test', 5, 'A new favourite',
           'This has quickly become one of my favourite wardrobe pieces. Very easy to style.', 1, 8, 8
    UNION ALL SELECT 'dev.review@moodbuds.test', 3, 'Nice, but check the fit',
           'The quality is good, although I would recommend checking the size chart carefully.', 0, 2, 10
    UNION ALL SELECT 'esha.review@moodbuds.test', 5, 'Lovely fabric and finish',
           'Soft fabric, clean finishing and a flattering look. It stayed comfortable all day.', 1, 4, 12
    UNION ALL SELECT 'farhan.review@moodbuds.test', 4, 'Looks premium',
           'The product has a premium appearance and arrived in excellent condition.', 1, 1, 14
    UNION ALL SELECT 'isha.review@moodbuds.test', 5, 'Perfect for an occasion',
           'Wore this for a special occasion and received several compliments. Very pleased.', 1, 7, 16
    UNION ALL SELECT 'jai.review@moodbuds.test', 4, 'Would recommend',
           'Good overall fit, finish and value. I would happily recommend it to a friend.', 0, 2, 18
) seed
JOIN users reviewer ON reviewer.email = seed.email AND reviewer.is_active = 1
JOIN products target ON target.id = (
    SELECT MIN(p.id)
    FROM products p
    WHERE p.is_active = 1 AND p.publication_status = 'PUBLISHED'
)
LEFT JOIN product_reviews existing
       ON existing.product_id = target.id AND existing.user_id = reviewer.id
WHERE existing.id IS NULL;
