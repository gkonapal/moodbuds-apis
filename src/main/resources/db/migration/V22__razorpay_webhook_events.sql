-- The base schema allowed only one payment row per order. Retrying a failed provider
-- attempt requires multiple rows, while (order_id,idempotency_key) remains unique.
ALTER TABLE payments DROP INDEX uq_payments_order;

CREATE TABLE payment_webhook_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME NULL,
    UNIQUE KEY uq_payment_webhook_provider_event (provider, provider_event_id),
    KEY idx_payment_webhook_received (received_at)
);
