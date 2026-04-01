ALTER TABLE system_configuration
    ADD COLUMN IF NOT EXISTS uzcard_rail VARCHAR(32) NOT NULL DEFAULT 'OSON';

ALTER TABLE system_configuration
    ADD COLUMN IF NOT EXISTS humo_legacy_dual_check_end TIMESTAMPTZ NULL;
