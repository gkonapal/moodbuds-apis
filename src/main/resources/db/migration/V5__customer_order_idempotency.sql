ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER user_id,
    ADD COLUMN idempotency_fingerprint CHAR(64) NULL AFTER idempotency_key,
    ADD CONSTRAINT uq_orders_customer_idempotency UNIQUE (user_id, idempotency_key);
