package com.example.shade.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs before Hibernate ddl-auto so existing {@code ticket_listing} rows get
 * {@code side='SELL'} before Hibernate tries {@code ADD COLUMN ... NOT NULL}.
 */
@Component
public class EarlySchemaFixer implements BeanPostProcessor, PriorityOrdered {

    private static final Logger logger = LoggerFactory.getLogger(EarlySchemaFixer.class);
    private final AtomicBoolean done = new AtomicBoolean(false);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && done.compareAndSet(false, true)) {
            ensureTicketListingSide(dataSource);
        }
        return bean;
    }

    private void ensureTicketListingSide(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
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
            // Nullable first — safe when rows already exist (Hibernate's NOT NULL add fails otherwise).
            statement.execute("ALTER TABLE ticket_listing ADD COLUMN IF NOT EXISTS side VARCHAR(16)");
            statement.execute("UPDATE ticket_listing SET side = 'SELL' WHERE side IS NULL");
            statement.execute("ALTER TABLE ticket_listing ALTER COLUMN side SET DEFAULT 'SELL'");
            statement.execute("ALTER TABLE ticket_listing ALTER COLUMN side SET NOT NULL");
            logger.info("Ensured ticket_listing.side exists and is backfilled to SELL");
        } catch (Exception e) {
            logger.warn("Could not pre-migrate ticket_listing.side: {}", e.getMessage());
            done.set(false);
        }
    }
}
