-- Hibernate ddl-auto may recreate system_configuration_uzcard_rail_check without OFF.
-- Align CHECK with UzcardRail enum (nullable column).

ALTER TABLE system_configuration
    DROP CONSTRAINT IF EXISTS system_configuration_uzcard_rail_check;

ALTER TABLE system_configuration
    ADD CONSTRAINT system_configuration_uzcard_rail_check
        CHECK (uzcard_rail IS NULL OR uzcard_rail::text = ANY (ARRAY['OSON', 'CARDXABAR', 'OFF']));
