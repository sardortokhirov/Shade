-- Turn on/off Wallet section (same as topup, withdraw, bonus)
-- Add as nullable first so existing rows get a value, then backfill and set NOT NULL
ALTER TABLE feature_settings
    ADD COLUMN IF NOT EXISTS wallet_enabled BOOLEAN DEFAULT true;
UPDATE feature_settings SET wallet_enabled = true WHERE wallet_enabled IS NULL;
ALTER TABLE feature_settings
    ALTER COLUMN wallet_enabled SET NOT NULL;
