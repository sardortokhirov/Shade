ALTER TABLE feature_settings
  ADD COLUMN IF NOT EXISTS bonus_auto_approve_enabled BOOLEAN NOT NULL DEFAULT false;
