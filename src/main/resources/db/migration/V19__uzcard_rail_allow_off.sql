-- Allow system_configuration.uzcard_rail = 'OFF' (Hibernate or manual CHECK may block it).

ALTER TABLE system_configuration
    DROP CONSTRAINT IF EXISTS system_configuration_uzcard_rail_check;

-- If a DB-specific enum type exists, column stays VARCHAR from JPA STRING enum mapping.
