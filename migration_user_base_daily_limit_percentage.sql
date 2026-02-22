-- Per-user base daily limit as percentage of system dailyBonusTransferLimit. Null = 100%.
ALTER TABLE user_limit_increase
ADD COLUMN IF NOT EXISTS base_daily_limit_percentage INTEGER NULL;

COMMENT ON COLUMN user_limit_increase.base_daily_limit_percentage IS 'Base daily limit as % of system limit (e.g. 100 = 100%, 150 = 150%). Null = 100%.';
