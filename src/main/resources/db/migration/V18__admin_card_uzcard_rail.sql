-- Per-card UZCARD rail + uniqueness within (oson_config, PAN, rail) for UZCARD and (oson_config, PAN) for HUMO.

ALTER TABLE admin_card
    ADD COLUMN IF NOT EXISTS uzcard_rail VARCHAR(32) NULL;

UPDATE admin_card ac
SET uzcard_rail = sub.uzcard_rail
FROM (
    SELECT uzcard_rail
    FROM system_configuration
    ORDER BY id DESC
    LIMIT 1
) sub
WHERE ac.payment_system = 'UZCARD'
  AND ac.uzcard_rail IS NULL;

UPDATE admin_card
SET uzcard_rail = 'OSON'
WHERE payment_system = 'UZCARD'
  AND uzcard_rail IS NULL;

ALTER TABLE admin_card
    ADD CONSTRAINT admin_card_uzcard_rail_check
        CHECK (
            (payment_system <> 'UZCARD') OR (uzcard_rail IS NOT NULL)
            );

CREATE UNIQUE INDEX IF NOT EXISTS admin_card_uz_oson_config_pan_rail
    ON admin_card (oson_config_id, card_number, uzcard_rail)
    WHERE payment_system = 'UZCARD';

CREATE UNIQUE INDEX IF NOT EXISTS admin_card_humo_oson_config_pan
    ON admin_card (oson_config_id, card_number)
    WHERE payment_system = 'HUMO';
