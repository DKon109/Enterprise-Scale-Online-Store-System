package com.comp5348.store.order.infrastructure.persistence;

import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.domain.repository.OrderRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PostgresOrderRepository implements OrderRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO orders (order_id, customer_id, item_id, quantity, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (order_id) DO UPDATE SET
                customer_id = EXCLUDED.customer_id,
                item_id = EXCLUDED.item_id,
                quantity = EXCLUDED.quantity,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """;

    private static final String SELECT_SQL = """
            SELECT order_id, customer_id, item_id, quantity, status, created_at, updated_at
            FROM orders
            WHERE order_id = ?
            """;

    private final PostgresConnectionProvider connectionProvider;

    public PostgresOrderRepository(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public void save(Order order) {
        Objects.requireNonNull(order, "order");
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setObject(1, order.getOrderId());
            statement.setString(2, order.getCustomerId());
            statement.setString(3, order.getItemId());
            statement.setInt(4, order.getQuantity());
            statement.setString(5, order.getStatus().name());
            statement.setTimestamp(6, Timestamp.from(order.getCreatedAt()));
            statement.setTimestamp(7, Timestamp.from(order.getUpdatedAt()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save order %s".formatted(order.getOrderId()), e);
        }
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
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
            throw new IllegalStateException("Failed to load order %s".formatted(orderId), e);
        }
    }

    private Order mapRow(ResultSet resultSet) throws SQLException {
        UUID orderId = resultSet.getObject("order_id", UUID.class);
        String customerId = resultSet.getString("customer_id");
        String itemId = resultSet.getString("item_id");
        int quantity = resultSet.getInt("quantity");
        Order.Status status = Order.Status.valueOf(resultSet.getString("status"));
        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
        Instant updatedAt = resultSet.getTimestamp("updated_at").toInstant();
        return Order.rehydrate(orderId, customerId, itemId, quantity, status, createdAt, updatedAt);
    }
}
