package com.example.shade.config;

import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Keeps PostgreSQL enum check constraints in sync with Java enums.
 *
 * Hibernate ddl-auto=update adds new columns, but it does not reliably update
 * existing enum CHECK constraints. Without this, new wallet request types like
 * WALLET_TO_PLATFORM and WALLET_TO_WALLET can fail at insert time on production
 * databases that were created before those features.
 */
@Component
@RequiredArgsConstructor
public class DatabaseConstraintMigration implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConstraintMigration.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureWalletP2pColumns();
        migrateHizmatRequestEnumChecks();
    }

    private void ensureWalletP2pColumns() {
        jdbcTemplate.execute("ALTER TABLE hizmat_request ADD COLUMN IF NOT EXISTS recipient_chat_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE hizmat_request ADD COLUMN IF NOT EXISTS fee_amount BIGINT");
        jdbcTemplate.execute("ALTER TABLE hizmat_request ADD COLUMN IF NOT EXISTS net_amount BIGINT");
        jdbcTemplate.execute("ALTER TABLE system_configuration ADD COLUMN IF NOT EXISTS wallet_to_wallet_fee_percentage NUMERIC(9,8)");
        jdbcTemplate.execute("ALTER TABLE lottery_configuration ADD COLUMN IF NOT EXISTS p2p_min_price_per_ticket BIGINT NOT NULL DEFAULT 1");
        jdbcTemplate.execute("ALTER TABLE lottery_configuration ADD COLUMN IF NOT EXISTS p2p_fee_percentage NUMERIC(9,8) NOT NULL DEFAULT 0");
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
        jdbcTemplate.execute("ALTER TABLE ticket_listing ADD COLUMN IF NOT EXISTS side VARCHAR(16)");
        jdbcTemplate.execute("UPDATE ticket_listing SET side = 'SELL' WHERE side IS NULL");
        jdbcTemplate.execute("ALTER TABLE ticket_listing ALTER COLUMN side SET DEFAULT 'SELL'");
        jdbcTemplate.execute("ALTER TABLE ticket_listing ALTER COLUMN side SET NOT NULL");
        logger.info("Ensured wallet P2P / ticket marketplace columns exist");
    }

    private void migrateHizmatRequestEnumChecks() {
        try {
            recreateCheckConstraint(
                    "hizmat_request",
                    "hizmat_request_type_check",
                    "type",
                    Arrays.stream(RequestType.values()).map(Enum::name).collect(Collectors.toList()));

            recreateCheckConstraint(
                    "hizmat_request",
                    "hizmat_request_status_check",
                    "status",
                    Arrays.stream(RequestStatus.values()).map(Enum::name).collect(Collectors.toList()));

            logger.info("Database enum check constraints are up to date for hizmat_request");
        } catch (Exception e) {
            logger.error("Failed to migrate hizmat_request enum check constraints: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void recreateCheckConstraint(String tableName, String constraintName, String columnName, Collection<String> allowedValues) {
        String values = toSqlInList(allowedValues);
        jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT IF EXISTS " + constraintName);
        jdbcTemplate.execute("ALTER TABLE " + tableName
                + " ADD CONSTRAINT " + constraintName
                + " CHECK (" + columnName + "::text = ANY (ARRAY[" + values + "]::text[]))");
        logger.info("Recreated {} on {}.{} with values [{}]", constraintName, tableName, columnName, values);
    }

    private String toSqlInList(Collection<String> values) {
        return String.join(", ",
                values.stream()
                        .map(value -> "'" + value.replace("'", "''") + "'")
                        .collect(Collectors.toList()));
    }
}
