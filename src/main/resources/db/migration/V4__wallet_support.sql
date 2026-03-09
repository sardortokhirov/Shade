-- Add wallet_balance column to user_balance table
ALTER TABLE user_balance ADD COLUMN IF NOT EXISTS wallet_balance BIGINT NOT NULL DEFAULT 0;

-- Add wallet min withdraw amount to system_configuration
ALTER TABLE system_configuration ADD COLUMN IF NOT EXISTS wallet_min_withdraw_amount BIGINT NOT NULL DEFAULT 10000;
