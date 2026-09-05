ALTER TABLE orders
    MODIFY COLUMN payment_method ENUM('UPI','CREDIT_CARD','DEBIT_CARD','NET_BANKING','WALLET','COD') NULL,
    ADD COLUMN shipping_pricing_status ENUM('PENDING','FINALIZED') NOT NULL DEFAULT 'FINALIZED' AFTER shipping_cost,
    ADD COLUMN shipping_pricing_source VARCHAR(50) NOT NULL DEFAULT 'FREE_SHIPPING_TEMPORARY' AFTER shipping_pricing_status;

ALTER TABLE payments
    MODIFY COLUMN method ENUM('UPI','CREDIT_CARD','DEBIT_CARD','NET_BANKING','WALLET','COD') NULL,
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER order_id,
    ADD CONSTRAINT uq_payments_order_idempotency UNIQUE (order_id, idempotency_key),
    ADD CONSTRAINT uq_payments_gateway_order UNIQUE (gateway_order_id),
    ADD CONSTRAINT uq_payments_gateway_payment UNIQUE (gateway_payment_id);
