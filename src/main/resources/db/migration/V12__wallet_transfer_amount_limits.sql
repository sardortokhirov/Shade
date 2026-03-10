-- Wallet -> Platform transfer amount limits (separate from card top-up limits)
ALTER TABLE system_configuration
    ADD COLUMN IF NOT EXISTS wallet_transfer_min_amount BIGINT NOT NULL DEFAULT 5000;

ALTER TABLE system_configuration
    ADD COLUMN IF NOT EXISTS wallet_transfer_max_amount BIGINT NOT NULL DEFAULT 10000000;

