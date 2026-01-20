-- Migration script to add carryover_amount column to daily_user_stats table
-- This column stores the temporary limit carryover from previous day
-- Run this script on your PostgreSQL database

ALTER TABLE daily_user_stats 
ADD COLUMN IF NOT EXISTS carryover_amount BIGINT NOT NULL DEFAULT 0;

-- Add comment to explain the column
COMMENT ON COLUMN daily_user_stats.carryover_amount IS 'Temporary limit carryover from previous day. Consumed first when user transfers, decreased when user deposits.';
