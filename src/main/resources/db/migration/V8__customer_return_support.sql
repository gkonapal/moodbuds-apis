ALTER TABLE order_items
    ADD COLUMN return_window_days_snapshot TINYINT UNSIGNED NULL AFTER line_total;

UPDATE order_items oi JOIN products p ON p.id=oi.product_id
SET oi.return_window_days_snapshot=p.return_window_days
WHERE oi.return_window_days_snapshot IS NULL;

ALTER TABLE order_items
    MODIFY COLUMN return_window_days_snapshot TINYINT UNSIGNED NOT NULL;

ALTER TABLE return_requests
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER user_id,
    ADD COLUMN idempotency_fingerprint CHAR(64) NULL AFTER idempotency_key,
    ADD COLUMN pickup_address_snapshot_full JSON NULL AFTER pickup_address_id,
    ADD CONSTRAINT uq_returns_customer_idempotency UNIQUE (user_id,idempotency_key);

ALTER TABLE return_items
    ADD CONSTRAINT uq_return_request_item UNIQUE (return_request_id,order_item_id);
