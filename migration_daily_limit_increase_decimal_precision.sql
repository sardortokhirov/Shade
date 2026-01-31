-- Migration: Change daily_limit_increase from BIGINT to DECIMAL(30,8)
-- This allows storing 8 decimal places for precise limit tracking
-- Date: 2026-01-26

-- Alter the column type from BIGINT to DECIMAL(30,8)
-- Existing integer values will be preserved (e.g., 1000 becomes 1000.00000000)
ALTER TABLE daily_user_stats 
ALTER COLUMN daily_limit_increase TYPE DECIMAL(30,8);

-- Set default value for new records
ALTER TABLE daily_user_stats 
ALTER COLUMN daily_limit_increase SET DEFAULT 0;
