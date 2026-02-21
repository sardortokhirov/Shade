-- Migration script to add daily_promo_transfer_amount column to daily_user_stats table
-- This column tracks transfers made while promo was enabled; not counted against limit after promo is turned off
-- Run this script on your PostgreSQL database (safe for tables with existing rows)

-- Step 1: Add column (nullable first so existing rows are valid)
ALTER TABLE daily_user_stats
ADD COLUMN IF NOT EXISTS daily_promo_transfer_amount BIGINT;

-- Step 2: Backfill NULLs with 0
UPDATE daily_user_stats
SET daily_promo_transfer_amount = 0
WHERE daily_promo_transfer_amount IS NULL;

-- Step 3: Set default and NOT NULL
ALTER TABLE daily_user_stats
ALTER COLUMN daily_promo_transfer_amount SET DEFAULT 0;

ALTER TABLE daily_user_stats
ALTER COLUMN daily_promo_transfer_amount SET NOT NULL;

COMMENT ON COLUMN daily_user_stats.daily_promo_transfer_amount IS 'Transfers made while promo was on; forgiven (not counted) when calculating limit after promo is off.';
