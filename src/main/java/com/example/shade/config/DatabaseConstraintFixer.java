package com.example.shade.config;

import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import com.example.shade.model.UzcardRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Runs database schema fixes on application startup.
 * This handles CHECK constraints that Hibernate's ddl-auto=update cannot modify
 * when new enum values are added.
 */
@Component
public class DatabaseConstraintFixer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConstraintFixer.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseConstraintFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void fixConstraints() {
        fixEnumConstraint("hizmat_request", "type", "hizmat_request_type_check", RequestType.class);
        fixEnumConstraint("hizmat_request", "status", "hizmat_request_status_check", RequestStatus.class);
        fixNullableEnumConstraint("system_configuration", "uzcard_rail", "system_configuration_uzcard_rail_check",
                UzcardRail.class);
        fixBotTipConfigurationColumns();
        fixWalletP2pAndTicketTradeColumns();
    }

    private void fixWalletP2pAndTicketTradeColumns() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE hizmat_request ADD COLUMN IF NOT EXISTS recipient_chat_id BIGINT");
            jdbcTemplate.execute(
                    "ALTER TABLE hizmat_request ADD COLUMN IF NOT EXISTS fee_amount BIGINT");
            jdbcTemplate.execute(
                    "ALTER TABLE hizmat_request ADD COLUMN IF NOT EXISTS net_amount BIGINT");
            jdbcTemplate.execute(
                    "ALTER TABLE system_configuration ADD COLUMN IF NOT EXISTS wallet_to_wallet_fee_percentage NUMERIC(9,8) NOT NULL DEFAULT 0");
            jdbcTemplate.execute(
                    "ALTER TABLE lottery_configuration ADD COLUMN IF NOT EXISTS p2p_min_price_per_ticket BIGINT NOT NULL DEFAULT 1");
            jdbcTemplate.execute(
                    "ALTER TABLE lottery_configuration ADD COLUMN IF NOT EXISTS p2p_fee_percentage NUMERIC(9,8) NOT NULL DEFAULT 0");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS ticket_listing ("
                            + "id BIGSERIAL PRIMARY KEY, "
                            + "seller_chat_id BIGINT NOT NULL, "
                            + "side VARCHAR(16) NOT NULL DEFAULT 'SELL', "
                            + "ticket_quantity BIGINT NOT NULL, "
                            + "total_price BIGINT NOT NULL, "
                            + "status VARCHAR(32) NOT NULL, "
                            + "buyer_chat_id BIGINT, "
                            + "fee_amount BIGINT, "
                            + "net_amount BIGINT, "
                            + "created_at TIMESTAMP NOT NULL, "
                            + "sold_at TIMESTAMP)");
            jdbcTemplate.execute(
                    "ALTER TABLE ticket_listing ADD COLUMN IF NOT EXISTS side VARCHAR(16) NOT NULL DEFAULT 'SELL'");
            logger.info("Ensured wallet P2P / lottery trade columns and ticket_listing table exist");
        } catch (Exception e) {
            logger.warn("Could not ensure wallet P2P / lottery trade schema: {}", e.getMessage());
        }
    }

    private void fixBotTipConfigurationColumns() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS min_bonus_tickets BIGINT NOT NULL DEFAULT 0");
            jdbcTemplate.execute(
                    "ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS max_bonus_tickets BIGINT NOT NULL DEFAULT 0");
            jdbcTemplate.execute(
                    "ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS bonus_tickets_enabled BOOLEAN NOT NULL DEFAULT true");
            jdbcTemplate.execute(
                    "ALTER TABLE bot_tip_configuration ADD COLUMN IF NOT EXISTS bonus_tickets_chance INTEGER NOT NULL DEFAULT 100");
            logger.info("Ensured bot_tip_configuration bonus columns exist");
        } catch (Exception e) {
            logger.warn("Could not add bot_tip_configuration bonus columns: {}", e.getMessage());
        }
    }

    private <E extends Enum<E>> void fixEnumConstraint(String table, String column, String constraintName,
            Class<E> enumClass) {
        try {
            String values = Arrays.stream(enumClass.getEnumConstants())
                    .map(e -> "'" + e.name() + "'")
                    .collect(Collectors.joining(","));

            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);

            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " ADD CONSTRAINT " + constraintName +
                            " CHECK (" + column + "::text = ANY (ARRAY[" + values + "]))");

            logger.info("Updated constraint {} with values: [{}]", constraintName, values);
        } catch (Exception e) {
            logger.warn("Could not update constraint {}: {}", constraintName, e.getMessage());
        }
    }

    /**
     * Same as {@link #fixEnumConstraint} but allows NULL (e.g. {@code system_configuration.uzcard_rail}).
     */
    private <E extends Enum<E>> void fixNullableEnumConstraint(String table, String column, String constraintName,
            Class<E> enumClass) {
        try {
            String values = Arrays.stream(enumClass.getEnumConstants())
                    .map(e -> "'" + e.name() + "'")
                    .collect(Collectors.joining(","));

            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);

            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " ADD CONSTRAINT " + constraintName +
                            " CHECK (" + column + " IS NULL OR " + column + "::text = ANY (ARRAY[" + values + "]))");

            logger.info("Updated nullable enum constraint {} with values: [{}]", constraintName, values);
        } catch (Exception e) {
            logger.warn("Could not update constraint {}: {}", constraintName, e.getMessage());
        }
    }
}
