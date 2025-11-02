package com.comp5348.store.order.infrastructure.persistence;

import com.comp5348.store.order.domain.model.OrderTimelineEntry;
import com.comp5348.store.order.domain.repository.OrderEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PostgresOrderEventRepository implements OrderEventRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String INSERT_SQL = """
            INSERT INTO order_timeline (order_id, event_type, payload, occurred_at)
            VALUES (?, ?, CAST(? AS jsonb), ?)
            """;

    private static final String SELECT_SQL = """
            SELECT order_id, event_type, payload, occurred_at
            FROM order_timeline
            WHERE order_id = ?
            ORDER BY occurred_at ASC, id ASC
            """;

    private final PostgresConnectionProvider connectionProvider;

    public PostgresOrderEventRepository(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public void record(OrderTimelineEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setObject(1, entry.getOrderId());
            statement.setString(2, entry.getEventType());
            statement.setString(3, toJson(entry.getPayload()));
            statement.setTimestamp(4, Timestamp.from(entry.getOccurredAt()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record timeline entry for order %s".formatted(entry.getOrderId()), e);
        }
    }

    @Override
    public List<OrderTimelineEntry> findByOrderId(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId");
        List<OrderTimelineEntry> entries = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setObject(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(mapRow(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load timeline for order %s".formatted(orderId), e);
        }
        return entries;
    }

    private OrderTimelineEntry mapRow(ResultSet resultSet) throws SQLException {
        UUID orderId = resultSet.getObject("order_id", UUID.class);
        String eventType = resultSet.getString("event_type");
        String payload = resultSet.getString("payload");
        Instant occurredAt = resultSet.getTimestamp("occurred_at").toInstant();
        return new OrderTimelineEntry(orderId, eventType, fromJson(payload), occurredAt);
    }

    private String toJson(Map<String, ?> payload) {
        Map<String, ?> safePayload = Optional.ofNullable(payload).orElse(Map.of());
        try {
            return OBJECT_MAPPER.writeValueAsString(safePayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize timeline payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize timeline payload", e);
        }
    }
}
