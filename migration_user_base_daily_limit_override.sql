-- Per-user base daily limit override. If null, system default is used.
ALTER TABLE user_limit_increase
ADD COLUMN IF NOT EXISTS base_daily_limit_override BIGINT NULL;

COMMENT ON COLUMN user_limit_increase.base_daily_limit_override IS 'Per-user base daily limit (UZS). Null = use system default.';
