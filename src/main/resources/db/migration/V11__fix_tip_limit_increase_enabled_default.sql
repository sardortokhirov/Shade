-- Fix: existing rows + Hibernate ddl-auto=update can create NULLs / fail NOT NULL adds.
-- Ensure column exists, backfill NULLs, and enforce NOT NULL with default.

ALTER TABLE bot_tip_configuration
    ADD COLUMN IF NOT EXISTS tip_limit_increase_enabled BOOLEAN;

UPDATE bot_tip_configuration
SET tip_limit_increase_enabled = FALSE
WHERE tip_limit_increase_enabled IS NULL;

ALTER TABLE bot_tip_configuration
    ALTER COLUMN tip_limit_increase_enabled SET DEFAULT FALSE;

ALTER TABLE bot_tip_configuration
    ALTER COLUMN tip_limit_increase_enabled SET NOT NULL;

