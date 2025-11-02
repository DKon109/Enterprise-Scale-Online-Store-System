package com.comp5348.store.order.infrastructure.persistence;

import com.comp5348.store.order.domain.model.OrderSagaState;
import com.comp5348.store.order.domain.repository.OrderSagaStateRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PostgresSagaStateRepository implements OrderSagaStateRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO order_saga_states (order_id, step, retries, last_error, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (order_id) DO UPDATE SET
                step = EXCLUDED.step,
                retries = EXCLUDED.retries,
                last_error = EXCLUDED.last_error,
                updated_at = EXCLUDED.updated_at
            """;

    private static final String SELECT_SQL = """
            SELECT order_id, step, retries, last_error, updated_at
            FROM order_saga_states
            WHERE order_id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM order_saga_states WHERE order_id = ?";

    private final PostgresConnectionProvider connectionProvider;

    public PostgresSagaStateRepository(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public void save(OrderSagaState state) {
        Objects.requireNonNull(state, "state");
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setObject(1, state.getOrderId());
            statement.setString(2, state.getStep());
            statement.setInt(3, state.getRetries());
            statement.setString(4, state.getLastError());
            statement.setTimestamp(5, Timestamp.from(state.getUpdatedAt()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist saga state for order %s".formatted(state.getOrderId()), e);
        }
    }

    @Override
    public Optional<OrderSagaState> findById(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId");
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setObject(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load saga state for order %s".formatted(orderId), e);
        }
    }

    @Override
    public void delete(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId");
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setObject(1, orderId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete saga state for order %s".formatted(orderId), e);
        }
    }

    private OrderSagaState mapRow(ResultSet resultSet) throws SQLException {
        UUID orderId = resultSet.getObject("order_id", UUID.class);
        String step = resultSet.getString("step");
        int retries = resultSet.getInt("retries");
        String lastError = resultSet.getString("last_error");
        Instant updatedAt = resultSet.getTimestamp("updated_at").toInstant();
        return new OrderSagaState(orderId, step, retries, lastError, updatedAt);
    }
}
