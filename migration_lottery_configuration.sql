-- Migration script for lottery configuration persistence
-- Run this script on your PostgreSQL database

-- Create lottery_configuration table
CREATE TABLE IF NOT EXISTS lottery_configuration (
    id BIGSERIAL PRIMARY KEY,
    purchase_cooldown_seconds BIGINT NOT NULL DEFAULT 300,
    winnings_percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Note: The application will automatically create a default configuration record on startup
-- if none exists via LotteryConfigService.init() method
