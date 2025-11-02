package com.comp5348.store.order.infrastructure;

import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.domain.model.OrderSagaState;
import com.comp5348.store.order.domain.model.OrderTimelineEntry;
import com.comp5348.store.order.infrastructure.persistence.PostgresConnectionProvider;
import com.comp5348.store.order.infrastructure.persistence.PostgresOrderEventRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresOrderRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresSagaStateRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

class PostgresRepositoriesTest {

    private EmbeddedPostgres postgres;
    private PostgresConnectionProvider connectionProvider;
    private PostgresOrderRepository orderRepository;
    private PostgresOrderEventRepository eventRepository;
    private PostgresSagaStateRepository sagaRepository;

    @BeforeEach
    void setUp() {
        postgres = startEmbeddedPostgresOrSkip();
        connectionProvider = new PostgresConnectionProvider(
                postgres.getJdbcUrl("postgres", "postgres"),
                "postgres",
                "postgres");
        orderRepository = new PostgresOrderRepository(connectionProvider);
        eventRepository = new PostgresOrderEventRepository(connectionProvider);
        sagaRepository = new PostgresSagaStateRepository(connectionProvider);
    }

    @AfterEach
    void tearDown() {
        if (postgres == null) {
            return;
        }
        try {
            postgres.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stop embedded Postgres", e);
        }
    }

    @Test
    void orderRepositoryPersistsAndLoadsAggregate() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, "cust-42", "SKU-99", 5);

        orderRepository.save(order);

        Order reloaded = orderRepository.getRequired(orderId);
        assertEquals(orderId, reloaded.getOrderId());
        assertEquals(order.getCustomerId(), reloaded.getCustomerId());
        assertEquals(order.getItemId(), reloaded.getItemId());
        assertEquals(order.getQuantity(), reloaded.getQuantity());
        assertEquals(order.getStatus(), reloaded.getStatus());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
    }

    @Test
    void orderEventRepositoryStoresTimeline() {
        UUID orderId = UUID.randomUUID();
        orderRepository.save(new Order(orderId, "cust-42", "SKU-99", 1));
        OrderTimelineEntry entry = new OrderTimelineEntry(orderId, "OrderPlaced", Map.of("qty", 3), Instant.now());

        eventRepository.record(entry);
        List<OrderTimelineEntry> entries = eventRepository.findByOrderId(orderId);

        assertEquals(1, entries.size());
        assertEquals(entry.getEventType(), entries.get(0).getEventType());
        assertEquals(entry.getPayload(), entries.get(0).getPayload());
    }

    @Test
    void sagaRepositoryPersistsLifecycle() {
        UUID orderId = UUID.randomUUID();
        orderRepository.save(new Order(orderId, "cust-42", "SKU-99", 1));
        Instant now = Instant.parse("2024-05-01T10:15:30Z");
        OrderSagaState state = new OrderSagaState(orderId, "RESERVING_STOCK", 0, null, now);

        sagaRepository.save(state);
        OrderSagaState reloaded = sagaRepository.findById(orderId).orElseThrow();
        assertEquals(state.getOrderId(), reloaded.getOrderId());
        assertEquals(state.getStep(), reloaded.getStep());

        sagaRepository.delete(orderId);
        assertTrue(sagaRepository.findById(orderId).isEmpty());
    }

    private EmbeddedPostgres startEmbeddedPostgresOrSkip() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException | IllegalStateException e) {
            Assumptions.assumeTrue(false, "Embedded Postgres not available: " + e.getMessage());
            throw new IllegalStateException("Embedded Postgres not available", e);
        }
    }
}
