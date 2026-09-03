-- Existing sell listings must get side=SELL before NOT NULL can be applied.
-- Hibernate ddl-auto=update alone fails with: column "side" contains null values.

ALTER TABLE ticket_listing
    ADD COLUMN IF NOT EXISTS side VARCHAR(16);

UPDATE ticket_listing
SET side = 'SELL'
WHERE side IS NULL;

ALTER TABLE ticket_listing
    ALTER COLUMN side SET DEFAULT 'SELL';

ALTER TABLE ticket_listing
    ALTER COLUMN side SET NOT NULL;
