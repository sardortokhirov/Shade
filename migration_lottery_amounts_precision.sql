-- Migration: Support large lottery amounts (e.g. up to ~1e15) in user_balance and lottery_prizes.
-- Run only if your existing columns have smaller precision.

-- Optional: expand user_balance.balance to NUMERIC(30,2) if it was created with lower precision
-- ALTER TABLE user_balance
--     ALTER COLUMN balance TYPE NUMERIC(30,2) USING balance::NUMERIC(30,2);

-- Optional: expand lottery_prizes.amount to NUMERIC(30,2) if it was created with lower precision
-- ALTER TABLE lottery_prizes
--     ALTER COLUMN amount TYPE NUMERIC(30,2) USING amount::NUMERIC(30,2);
