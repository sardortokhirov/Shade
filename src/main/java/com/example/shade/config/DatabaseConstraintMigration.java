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
 * WALLET_TO_PLATFORM can fail at insert time on production databases that were
 * created before the wallet feature.
 */
@Component
@RequiredArgsConstructor
public class DatabaseConstraintMigration implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConstraintMigration.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrateHizmatRequestEnumChecks();
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
