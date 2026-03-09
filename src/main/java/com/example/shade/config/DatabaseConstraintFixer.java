package com.example.shade.config;

import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
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
        fixBotTipConfigurationColumns();
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
}
