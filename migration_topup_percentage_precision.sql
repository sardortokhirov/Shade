-- Migration script to increase precision of top_up_daily_limit_increase_percentage
-- Run this script on your PostgreSQL database
-- Changes the column from NUMERIC(5,4) to NUMERIC(9,8) to support 8 decimal places

-- Alter the column type to support 8 decimal places (precision 9, scale 8)
ALTER TABLE system_configuration 
ALTER COLUMN top_up_daily_limit_increase_percentage TYPE NUMERIC(9,8);

-- Note: Existing data will be automatically converted
-- Values like 0.1234 will remain as 0.12340000 (no data loss)
