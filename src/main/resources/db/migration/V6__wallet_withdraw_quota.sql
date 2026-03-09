-- Add wallet withdraw ratio to system_configuration
ALTER TABLE system_configuration
    ADD COLUMN IF NOT EXISTS wallet_withdraw_ratio BIGINT NOT NULL DEFAULT 10;

-- Create user_wallet_quota table to track per-user earned and used withdrawal quota
CREATE TABLE IF NOT EXISTS user_wallet_quota (
    chat_id       BIGINT PRIMARY KEY,
    earned_quota  BIGINT NOT NULL DEFAULT 0,
    used_quota    BIGINT NOT NULL DEFAULT 0
);
