-- Migration script to add daily_promo_transfer_amount column to daily_user_stats table
-- This column tracks transfers made while promo was enabled; not counted against limit after promo is turned off
-- Run this script on your PostgreSQL database

ALTER TABLE daily_user_stats
ADD COLUMN IF NOT EXISTS daily_promo_transfer_amount BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN daily_user_stats.daily_promo_transfer_amount IS 'Transfers made while promo was on; forgiven (not counted) when calculating limit after promo is off.';
