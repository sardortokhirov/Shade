-- Fix for daily_promo_transfer_amount: add column safely and fix existing NULLs
-- Run this if you get: column "daily_promo_transfer_amount" contains null values
-- PostgreSQL does not allow ADD COLUMN ... NOT NULL on a table with existing rows unless DEFAULT is specified.
-- This script works whether the column is missing or already exists with NULLs.

-- Step 1: Add column only if it doesn't exist (as nullable so existing rows get NULL, then we backfill)
ALTER TABLE daily_user_stats
ADD COLUMN IF NOT EXISTS daily_promo_transfer_amount BIGINT;

-- Step 2: Backfill any NULLs with 0 (required before setting NOT NULL)
UPDATE daily_user_stats
SET daily_promo_transfer_amount = 0
WHERE daily_promo_transfer_amount IS NULL;

-- Step 3: Enforce NOT NULL and default for new rows
ALTER TABLE daily_user_stats
ALTER COLUMN daily_promo_transfer_amount SET DEFAULT 0;

ALTER TABLE daily_user_stats
ALTER COLUMN daily_promo_transfer_amount SET NOT NULL;

COMMENT ON COLUMN daily_user_stats.daily_promo_transfer_amount IS 'Transfers made while promo was on; forgiven (not counted) when calculating limit after promo is off.';
