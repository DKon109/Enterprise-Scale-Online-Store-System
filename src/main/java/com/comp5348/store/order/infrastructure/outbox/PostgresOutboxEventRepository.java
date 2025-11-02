package com.comp5348.store.order.infrastructure.outbox;

import com.comp5348.store.order.domain.model.OutboxEvent;
import com.comp5348.store.order.domain.repository.OutboxEventRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresConnectionProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL-backed implementation of the outbox repository.
 */
public class PostgresOutboxEventRepository implements OutboxEventRepository {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS outbox_events (
                id BIGSERIAL PRIMARY KEY,
                aggregate_id UUID NOT NULL,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                published BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMPTZ NOT NULL
            )
            """;

    private static final String CREATE_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_outbox_events_published_id
            ON outbox_events(published, id)
            """;

    private final PostgresConnectionProvider connectionProvider;

    public PostgresOutboxEventRepository(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        initializeSchema();
    }

    private void initializeSchema() {
        try (Connection connection = connectionProvider.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE_SQL);
            statement.executeUpdate(CREATE_INDEX_SQL);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize outbox schema", e);
        }
    }

    @Override
    public OutboxEvent append(UUID aggregateId, String type, String payload) {
        if (aggregateId == null) {
            throw new IllegalArgumentException("aggregateId is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }

        Instant createdAt = Instant.now();
        String sql = """
                INSERT INTO outbox_events (aggregate_id, type, payload, published, created_at)
                VALUES (?, ?, ?, FALSE, ?)
                """;

        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, aggregateId);
            statement.setString(2, type);
            statement.setString(3, payload);
            statement.setTimestamp(4, Timestamp.from(createdAt));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("No generated key returned for outbox event");
                }
                long id = keys.getLong(1);
                return new OutboxEvent(id, aggregateId, type, payload, false, createdAt);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to append outbox event", e);
        }
    }

    @Override
    public List<OutboxEvent> findUnpublished(int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }

        String sql = """
                SELECT id, aggregate_id, type, payload, published, created_at
                FROM outbox_events
                WHERE published = FALSE
                ORDER BY id ASC
                LIMIT ?
                """;

        List<OutboxEvent> events = new ArrayList<>(batchSize);
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, batchSize);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(mapRow(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch unpublished outbox events", e);
        }

        return events;
    }

    @Override
    public void markPublished(long eventId) {
        String sql = "UPDATE outbox_events SET published = TRUE WHERE id = ?";

        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to mark outbox event as published", e);
        }
    }

    private OutboxEvent mapRow(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        UUID aggregateId = resultSet.getObject("aggregate_id", UUID.class);
        String type = resultSet.getString("type");
        String payload = resultSet.getString("payload");
        boolean published = resultSet.getBoolean("published");
        Timestamp createdAt = resultSet.getTimestamp("created_at");

        return new OutboxEvent(id, aggregateId, type, payload, published, createdAt.toInstant());
    }
}

