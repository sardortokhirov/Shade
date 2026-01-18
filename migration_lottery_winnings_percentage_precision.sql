-- Migration script to increase precision of winnings_percentage
-- Run this script on your PostgreSQL database
-- Changes the column from NUMERIC(5,2) to NUMERIC(9,8) to support 8 decimal places

-- Alter the column type to support 8 decimal places (precision 9, scale 8)
ALTER TABLE lottery_configuration
ALTER COLUMN winnings_percentage TYPE NUMERIC(9,8);

-- Note: Existing data will be automatically converted
-- Values like 0.12 will remain as 0.12000000 (no data loss)
