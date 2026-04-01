-- Add nullable first so existing rows do not violate NOT NULL; then backfill.
ALTER TABLE system_configuration
    ADD COLUMN IF NOT EXISTS uzcard_rail VARCHAR(32);

UPDATE system_configuration SET uzcard_rail = 'OSON' WHERE uzcard_rail IS NULL;

ALTER TABLE system_configuration
    ADD COLUMN IF NOT EXISTS humo_legacy_dual_check_end TIMESTAMPTZ NULL;
