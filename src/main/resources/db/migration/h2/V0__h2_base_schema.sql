-- H2 Base Schema for MoodBuds
-- H2 in MySQL mode compatibility layer
-- Ensures H2 database has same schema as MySQL migrations (V1-V18)
-- This file synchronizes schema between H2 (dev) and MySQL (prod)

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS failure_description VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS next_retry_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS last_reconciled_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS slug VARCHAR(360) UNIQUE NULL;

ALTER TABLE return_requests
    ADD COLUMN IF NOT EXISTS inventory_restocked_at DATETIME NULL;
