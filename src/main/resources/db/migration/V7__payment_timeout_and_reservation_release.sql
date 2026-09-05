ALTER TABLE orders
    ADD COLUMN payment_expires_at DATETIME NULL AFTER total_amount,
    ADD COLUMN cancellation_reason VARCHAR(255) NULL AFTER payment_expires_at,
    ADD COLUMN cancelled_at DATETIME NULL AFTER cancellation_reason,
    ADD COLUMN reservation_released_at DATETIME NULL AFTER cancelled_at;

UPDATE orders SET payment_expires_at=DATE_ADD(created_at, INTERVAL 15 MINUTE)
WHERE payment_expires_at IS NULL;

ALTER TABLE orders
    MODIFY COLUMN payment_expires_at DATETIME NOT NULL,
    ADD INDEX idx_orders_payment_expiry (status,payment_expires_at,reservation_released_at);

ALTER TABLE payments
    MODIFY COLUMN status ENUM('INITIATED','PENDING','SUCCESS','FAILED','EXPIRED','REFUNDED','PARTIALLY_REFUNDED')
        NOT NULL DEFAULT 'INITIATED';
