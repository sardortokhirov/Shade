-- Tip → permanent limit reward: for every X UZS tipped, add Y UZS doimiy limit
ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS tip_limit_increase_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS tip_limit_per_amount_uzs BIGINT;
ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS tip_limit_amount_uzs BIGINT;
