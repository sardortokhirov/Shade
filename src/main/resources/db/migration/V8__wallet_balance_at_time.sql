-- Store wallet balance at time of transaction for history display
ALTER TABLE hizmat_request
    ADD COLUMN IF NOT EXISTS wallet_balance_at_time BIGINT NULL;
