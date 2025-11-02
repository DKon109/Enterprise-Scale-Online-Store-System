package com.comp5348.store.order.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Executes idempotent schema migrations required by the ordering module.
 */
final class DatabaseMigrations {

    private static final String[] MIGRATION_STATEMENTS = {
            // Orders aggregate storage
            """
            CREATE TABLE IF NOT EXISTS orders (
                order_id UUID PRIMARY KEY,
                customer_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                quantity INTEGER NOT NULL CHECK (quantity > 0),
                status TEXT NOT NULL,
                created_at TIMESTAMPTZ NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL
            )
            """
    };

    private DatabaseMigrations() {
        // Utility
    }

    static void run(PostgresConnectionProvider connectionProvider) {
        try (Connection connection = connectionProvider.getConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : MIGRATION_STATEMENTS) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute database migrations", e);
        }
    }
}
