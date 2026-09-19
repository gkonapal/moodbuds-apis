-- Product reviews table
CREATE TABLE product_reviews (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
  title VARCHAR(255),
  comment TEXT,
  is_verified_purchase TINYINT NOT NULL DEFAULT 0,
  helpful_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_product_reviews_product (product_id),
  KEY idx_product_reviews_user (user_id),
  KEY idx_product_reviews_created (created_at),
  CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT fk_product_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Add sample reviews
INSERT INTO product_reviews (product_id, user_id, rating, title, comment, is_verified_purchase, created_at)
VALUES
  (1, 1, 5, 'Great quality!', 'Love this product. Excellent fit and comfortable. Highly recommend!', 1, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 DAY)),
  (1, 2, 4, 'Good value', 'Nice product. Delivery was quick. Would order again.', 1, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY)),
  (1, 3, 5, 'Perfect!', 'Exceeded expectations. Great color and material. Fits perfectly!', 1, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 2 DAY)),
  (1, 1, 3, 'Average', 'Decent product but expected better quality for the price.', 1, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)),
  (1, 2, 4, 'Worth buying', 'Good product overall. Minor issue with stitching but still happy with purchase.', 1, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 12 HOUR));
