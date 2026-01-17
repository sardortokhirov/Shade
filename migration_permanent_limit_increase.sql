-- Migration script for permanent limit increase feature
-- Run this script on your PostgreSQL database

-- Create user_limit_increase table
CREATE TABLE IF NOT EXISTS user_limit_increase (
    chat_id BIGINT PRIMARY KEY,
    accumulated_limit_increase BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add index for better performance
CREATE INDEX IF NOT EXISTS idx_user_limit_increase_chat_id ON user_limit_increase(chat_id);

-- Optional: Migrate existing dailyLimitIncrease values from daily_user_stats
-- This will sum all historical dailyLimitIncrease values per user
-- Uncomment if you want to migrate existing data:
/*
INSERT INTO user_limit_increase (chat_id, accumulated_limit_increase, last_updated)
SELECT 
    chat_id,
    COALESCE(SUM(daily_limit_increase), 0) as accumulated_limit_increase,
    MAX(last_updated) as last_updated
FROM daily_user_stats
WHERE daily_limit_increase > 0
GROUP BY chat_id
ON CONFLICT (chat_id) DO NOTHING;
*/
