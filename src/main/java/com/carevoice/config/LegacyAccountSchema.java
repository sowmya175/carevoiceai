package com.carevoice.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Keeps clinical tables intact. On PostgreSQL, the old email column stays but is no longer required,
 * and any account row that has no username receives {@code legacy-<id>} so it cannot collide with a new username.
 */
@Component
@Order(0)
public class LegacyAccountSchema implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyAccountSchema.class);
    private final DataSource dataSource;

    public LegacyAccountSchema(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase(Locale.ROOT).contains("postgres")) return;
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE user_accounts ALTER COLUMN email DROP NOT NULL");
                statement.execute("ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS username varchar(32)");
                int updated = statement.executeUpdate("""
                        UPDATE user_accounts
                        SET username = 'legacy-' || id
                        WHERE username IS NULL
                        """);
                if (updated > 0) {
                    log.info("Assigned usernames to {} existing account row(s). Those rows no longer sign in with email.", updated);
                }
            }
        } catch (SQLException exception) {
            log.warn("Could not adjust legacy account columns. sqlState={}", exception.getSQLState());
        }
    }
}
