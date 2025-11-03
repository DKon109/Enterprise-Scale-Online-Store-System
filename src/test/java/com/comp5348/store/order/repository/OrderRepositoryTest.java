package com.comp5348.store.order.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.comp5348.store.order.model.Order;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for OrderRepository.
 *
 * Uses @DataJpaTest to test JPA repository functionality with an embedded database.
 */
@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void saveOrderAndRetrieveById() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);

        // Act
        Order saved = orderRepository.save(order);
        Optional<Order> retrieved = orderRepository.findById(orderId);

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(orderId, retrieved.get().getOrderId());
        assertEquals(customerId, retrieved.get().getCustomerId());
        assertEquals("SKU-001", retrieved.get().getItemId());
        assertEquals(5, retrieved.get().getQuantity());
        assertEquals("PENDING", retrieved.get().getStatus().name());
    }

    @Test
    void findByCustomerId() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Order order1 = new Order(UUID.randomUUID(), customerId, "SKU-001", 2);
        Order order2 = new Order(UUID.randomUUID(), customerId, "SKU-002", 3);
        Order order3 = new Order(UUID.randomUUID(), UUID.randomUUID(), "SKU-003", 1);

        orderRepository.save(order1);
        orderRepository.save(order2);
        orderRepository.save(order3);

        // Act
        List<Order> customerOrders = orderRepository.findByCustomerId(customerId);

        // Assert
        assertEquals(2, customerOrders.size());
        assertTrue(customerOrders.stream().allMatch(o -> o.getCustomerId().equals(customerId)));
    }

    @Test
    void updateOrderStatus() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);
        orderRepository.save(order);

        // Act
        Order retrieved = orderRepository.findById(orderId).orElseThrow();
        retrieved.markReserved();
        Order updated = orderRepository.save(retrieved);

        // Assert
        assertEquals("RESERVED", updated.getStatus().name());
        assertTrue(updated.getUpdatedAt().isAfter(updated.getCreatedAt()));
    }

    @Test
    void orderNotFoundReturnsEmpty() {
        // Act
        Optional<Order> result = orderRepository.findById(UUID.randomUUID());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void existsByIdReturnsTrueForExistingOrder() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);
        orderRepository.save(order);

        // Act
        boolean exists = orderRepository.existsById(orderId);

        // Assert
        assertTrue(exists);
    }

    @Test
    void existsByIdReturnsFalseForNonExistingOrder() {
        // Act
        boolean exists = orderRepository.existsById(UUID.randomUUID());

        // Assert
        assertFalse(exists);
    }

    @Test
    void timestampsSetCorrectlyOnSave() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);
        Instant beforeSave = Instant.now();

        // Act
        Order saved = orderRepository.save(order);
        Instant afterSave = Instant.now();

        // Assert
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertTrue(saved.getCreatedAt().isAfter(beforeSave.minusSeconds(1)));
        assertTrue(saved.getCreatedAt().isBefore(afterSave.plusSeconds(1)));
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Test
    void multipleOrdersForSameCustomer() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Order order1 = new Order(UUID.randomUUID(), customerId, "SKU-001", 1);
        Order order2 = new Order(UUID.randomUUID(), customerId, "SKU-002", 2);
        Order order3 = new Order(UUID.randomUUID(), customerId, "SKU-003", 3);

        // Act
        orderRepository.saveAll(List.of(order1, order2, order3));
        List<Order> retrieved = orderRepository.findByCustomerId(customerId);

        // Assert
        assertEquals(3, retrieved.size());
    }

    @Test
    void deleteOrder() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);
        orderRepository.save(order);

        // Act
        orderRepository.deleteById(orderId);
        Optional<Order> retrieved = orderRepository.findById(orderId);

        // Assert
        assertTrue(retrieved.isEmpty());
    }
}

