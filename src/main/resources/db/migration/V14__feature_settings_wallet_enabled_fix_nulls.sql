-- Fix wallet_enabled when column already exists with NULLs (e.g. added by Hibernate without default)
UPDATE feature_settings SET wallet_enabled = true WHERE wallet_enabled IS NULL;
ALTER TABLE feature_settings
    ALTER COLUMN wallet_enabled SET NOT NULL;
