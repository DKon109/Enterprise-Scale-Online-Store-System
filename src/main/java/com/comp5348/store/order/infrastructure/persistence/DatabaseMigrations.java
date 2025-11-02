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
            """,
            // Timeline of significant order events
            """
            CREATE TABLE IF NOT EXISTS order_timeline (
                id BIGSERIAL PRIMARY KEY,
                order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
                event_type TEXT NOT NULL,
                payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                occurred_at TIMESTAMPTZ NOT NULL
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_order_timeline_order_id_occurred_at
            ON order_timeline(order_id, occurred_at)
            """,
            // Saga coordination state
            """
            CREATE TABLE IF NOT EXISTS order_saga_states (
                order_id UUID PRIMARY KEY REFERENCES orders(order_id) ON DELETE CASCADE,
                step TEXT NOT NULL,
                retries INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                updated_at TIMESTAMPTZ NOT NULL
            )
            """,
            // Outbox for reliable event publication
            """
            CREATE TABLE IF NOT EXISTS outbox_events (
                id BIGSERIAL PRIMARY KEY,
                aggregate_id UUID NOT NULL,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                published BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMPTZ NOT NULL
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_outbox_events_published_id
            ON outbox_events(published, id)
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
