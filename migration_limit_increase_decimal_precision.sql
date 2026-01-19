-- Migration script to change accumulated_limit_increase to NUMERIC(20,8) for precise decimal storage
-- Run this script on your PostgreSQL database
-- Changes the column from BIGINT to NUMERIC(20,8) to support 8 decimal places

-- Alter the column type to support precise decimal values (precision 20, scale 8)
ALTER TABLE user_limit_increase 
ALTER COLUMN accumulated_limit_increase TYPE NUMERIC(20,8) USING accumulated_limit_increase::NUMERIC(20,8);

-- Note: Existing BIGINT values will be automatically converted to NUMERIC
-- Values like 1000 will become 1000.00000000 (no data loss)
-- This allows storing precise decimal values like 0.1, 0.0001, etc.
