ALTER TABLE refunds
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER return_request_id,
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'INR' AFTER amount,
    ADD COLUMN speed_requested ENUM('NORMAL','OPTIMUM') NOT NULL DEFAULT 'OPTIMUM' AFTER currency,
    ADD COLUMN speed_processed VARCHAR(20) NULL AFTER speed_requested,
    ADD COLUMN provider_reference VARCHAR(255) NULL AFTER gateway_refund_id,
    ADD COLUMN failure_code VARCHAR(100) NULL AFTER gateway_response,
    ADD COLUMN failure_description VARCHAR(500) NULL AFTER failure_code,
    ADD COLUMN provider_attempt_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER failure_description,
    ADD COLUMN next_retry_at DATETIME NULL AFTER provider_attempt_count,
    ADD COLUMN last_reconciled_at DATETIME NULL AFTER next_retry_at,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER completed_at,
    ADD INDEX idx_refunds_return_request (return_request_id),
    ADD CONSTRAINT uq_refunds_idempotency UNIQUE (idempotency_key),
    ADD CONSTRAINT uq_refunds_gateway_refund UNIQUE (gateway_refund_id);

ALTER TABLE return_requests
    ADD COLUMN inventory_restocked_at DATETIME NULL AFTER images;

CREATE TABLE refund_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_id BIGINT UNSIGNED NOT NULL,
    return_item_id BIGINT UNSIGNED NOT NULL,
    amount INT UNSIGNED NOT NULL COMMENT 'Allocated refund amount in paise',
    PRIMARY KEY (id),
    UNIQUE KEY uq_refund_item (refund_id,return_item_id),
    KEY idx_refund_items_return_item (return_item_id),
    CONSTRAINT fk_refund_items_refund FOREIGN KEY (refund_id) REFERENCES refunds(id) ON DELETE CASCADE,
    CONSTRAINT fk_refund_items_return_item FOREIGN KEY (return_item_id) REFERENCES return_items(id)
);

ALTER TABLE orders
    MODIFY COLUMN status ENUM('PENDING_PAYMENT','PAYMENT_FAILED','PROCESSING','CONFIRMED','PACKED','SHIPPED',
        'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','RETURN_INITIATED','RETURN_PICKUP_SCHEDULED',
        'RETURN_RECEIVED','REFUND_INITIATED','PARTIALLY_REFUNDED','REFUNDED')
        NOT NULL DEFAULT 'PENDING_PAYMENT';
