-- Add random bonus ticket range for tips
ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS min_bonus_tickets BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS max_bonus_tickets BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS bonus_tickets_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS bonus_tickets_chance INTEGER NOT NULL DEFAULT 100;
