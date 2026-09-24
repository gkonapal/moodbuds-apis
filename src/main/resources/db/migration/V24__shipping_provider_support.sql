CREATE TABLE shipments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id BIGINT UNSIGNED NOT NULL,
    return_request_id BIGINT UNSIGNED NULL,
    direction ENUM('FORWARD','REVERSE') NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_order_id VARCHAR(100) NULL,
    provider_shipment_id VARCHAR(100) NULL,
    courier_company_id VARCHAR(100) NULL,
    courier_name VARCHAR(150) NULL,
    awb_code VARCHAR(100) NULL,
    status VARCHAR(60) NOT NULL DEFAULT 'BOOKING_PENDING',
    pickup_status VARCHAR(60) NULL,
    tracking_url VARCHAR(1000) NULL,
    shipping_charge INT NOT NULL DEFAULT 0,
    estimated_pickup_date DATE NULL,
    estimated_delivery_date DATE NULL,
    last_location VARCHAR(255) NULL,
    failure_code VARCHAR(100) NULL,
    failure_description VARCHAR(1000) NULL,
    provider_response JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_shipments_order_direction_return (order_id,direction,return_request_id),
    UNIQUE KEY uq_shipments_provider_shipment (provider,provider_shipment_id),
    KEY idx_shipments_order (order_id),
    KEY idx_shipments_return (return_request_id),
    KEY idx_shipments_status (status),
    CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_shipments_return FOREIGN KEY (return_request_id) REFERENCES return_requests(id)
);

CREATE TABLE shipment_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shipment_id BIGINT UNSIGNED NOT NULL,
    provider_event_key VARCHAR(191) NOT NULL,
    provider_status VARCHAR(100) NULL,
    normalized_status VARCHAR(60) NOT NULL,
    message VARCHAR(1000) NULL,
    location VARCHAR(255) NULL,
    event_at DATETIME NOT NULL,
    payload JSON NULL,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_shipment_event_provider_key (provider_event_key),
    KEY idx_shipment_events_shipment (shipment_id,event_at),
    CONSTRAINT fk_shipment_events_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE CASCADE
);

CREATE TABLE shipping_quotes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    address_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(30) NOT NULL,
    courier_company_id VARCHAR(100) NULL,
    courier_name VARCHAR(150) NULL,
    delivery_pincode VARCHAR(10) NOT NULL,
    weight_grams INT UNSIGNED NOT NULL,
    shipping_charge INT NOT NULL DEFAULT 0,
    estimated_delivery_date DATE NULL,
    serviceable TINYINT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shipping_quotes_lookup (user_id,address_id,expires_at),
    CONSTRAINT fk_shipping_quotes_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_shipping_quotes_address FOREIGN KEY (address_id) REFERENCES user_addresses(id)
);

ALTER TABLE orders
    ADD COLUMN shipping_quote_id BIGINT UNSIGNED NULL AFTER shipping_pricing_source,
    ADD COLUMN expected_delivery_date DATE NULL AFTER shipping_quote_id,
    ADD KEY idx_orders_shipping_quote (shipping_quote_id),
    ADD CONSTRAINT fk_orders_shipping_quote FOREIGN KEY (shipping_quote_id) REFERENCES shipping_quotes(id);

ALTER TABLE return_requests
    ADD COLUMN expected_pickup_date DATE NULL AFTER pickup_address_snapshot_full,
    ADD COLUMN expected_warehouse_arrival_date DATE NULL AFTER expected_pickup_date;
