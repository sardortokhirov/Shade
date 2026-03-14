-- Turn on/off Wallet section (same as topup, withdraw, bonus)
ALTER TABLE feature_settings
    ADD COLUMN IF NOT EXISTS wallet_enabled BOOLEAN NOT NULL DEFAULT true;
