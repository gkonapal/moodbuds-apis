-- H2 Base Schema for MoodBuds
-- H2 in MySQL mode compatibility layer
-- This migration ensures H2 database compatibility with existing MySQL migrations

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS provider_reference_id VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS failure_description VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS next_retry_at DATETIME NULL;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS slug VARCHAR(255) UNIQUE NULL;
