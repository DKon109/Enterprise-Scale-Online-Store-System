package com.comp5348.store.order.service;

import static org.junit.jupiter.api.Assertions.*;

import com.comp5348.store.order.exception.OrderNotFoundException;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.repository.OrderRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for OrderService with real database.
 *
 * COMPLIANCE REQUIREMENT: COMP5348 requires "all crucial data must be stored in a database (on disk)"
 * and "proper transactional support and data integrity".
 *
 * These tests verify:
 * - Service correctly persists data to database
 * - Transactions work properly
 * - State transitions persist correctly
 * - Business rules are enforced with real persistence
 *
 * This layer is essential because mocks alone cannot verify:
 * - Database schema is correct
 * - JPA/Hibernate mapping works
 * - Constraints are enforced
 * - Data actually persists
 */
@DataJpaTest
@Import(OrderService.class)
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void placeOrderPersistsToDatabase() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        String itemId = "SKU-001";
        int quantity = 5;

        // Act
        Order created = orderService.placeOrder(customerId, itemId, quantity);

        // Assert - verify in memory
        assertNotNull(created.getOrderId());
        assertEquals(customerId, created.getCustomerId());
        assertEquals(itemId, created.getItemId());
        assertEquals(quantity, created.getQuantity());
        assertEquals(Order.Status.PENDING, created.getStatus());

        // Assert - verify in database (COMPLIANCE: data persisted to disk)
        Order retrieved = orderRepository.findById(created.getOrderId()).orElseThrow();
        assertEquals(created.getOrderId(), retrieved.getOrderId());
        assertEquals(Order.Status.PENDING, retrieved.getStatus());
    }

    @Test
    void getOrderRetrievesFromDatabase() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Order created = orderService.placeOrder(customerId, "SKU-001", 5);

        // Act
        Order retrieved = orderService.getOrder(created.getOrderId());

        // Assert
        assertEquals(created.getOrderId(), retrieved.getOrderId());
        assertEquals(customerId, retrieved.getCustomerId());
    }

    @Test
    void getOrderNotFoundThrowsException() {
        // Act & Assert
        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getOrder(UUID.randomUUID()));
    }

    @Test
    void getCustomerOrdersReturnsAllCustomerOrders() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        orderService.placeOrder(customerId, "SKU-001", 2);
        orderService.placeOrder(customerId, "SKU-002", 3);
        orderService.placeOrder(UUID.randomUUID(), "SKU-003", 1);

        // Act
        List<Order> orders = orderService.getCustomerOrders(customerId);

        // Assert
        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.getCustomerId().equals(customerId)));
    }

    @Test
    void cancelOrderUpdatesStatusInDatabase() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Order created = orderService.placeOrder(customerId, "SKU-001", 5);

        // Act
        orderService.cancelOrder(created.getOrderId());

        // Assert - verify in database (COMPLIANCE: state transitions persist)
        Order retrieved = orderRepository.findById(created.getOrderId()).orElseThrow();
        assertEquals(Order.Status.CANCELLED, retrieved.getStatus());
    }

    @Test
    void cancelOrderAfterShipmentThrowsException() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Order created = orderService.placeOrder(customerId, "SKU-001", 5);

        // Transition to SHIPMENT_REQUESTED
        Order order = orderRepository.findById(created.getOrderId()).orElseThrow();
        order.markReserved();
        order.markPaid();
        order.markShipmentRequested();
        orderRepository.save(order);

        // Act & Assert
        assertThrows(
                IllegalStateException.class,
                () -> orderService.cancelOrder(created.getOrderId()));
    }

    @Test
    void orderExistsReturnsTrueForExistingOrder() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Order created = orderService.placeOrder(customerId, "SKU-001", 5);

        // Act
        boolean exists = orderService.orderExists(created.getOrderId());

        // Assert
        assertTrue(exists);
    }

    @Test
    void orderExistsReturnsFalseForNonExistentOrder() {
        // Act
        boolean exists = orderService.orderExists(UUID.randomUUID());

        // Assert
        assertFalse(exists);
    }

    @Test
    void multipleOrdersForSameCustomer() {
        // Arrange
        UUID customerId = UUID.randomUUID();

        // Act
        Order order1 = orderService.placeOrder(customerId, "SKU-001", 2);
        Order order2 = orderService.placeOrder(customerId, "SKU-002", 3);
        Order order3 = orderService.placeOrder(customerId, "SKU-003", 1);

        // Assert
        List<Order> orders = orderService.getCustomerOrders(customerId);
        assertEquals(3, orders.size());

        assertTrue(orders.stream().anyMatch(o -> o.getOrderId().equals(order1.getOrderId())));
        assertTrue(orders.stream().anyMatch(o -> o.getOrderId().equals(order2.getOrderId())));
        assertTrue(orders.stream().anyMatch(o -> o.getOrderId().equals(order3.getOrderId())));
    }

    @Test
    void orderStatusTransitionsArePersistedCorrectly() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Order created = orderService.placeOrder(customerId, "SKU-001", 5);

        // Act - transition through states
        Order order = orderRepository.findById(created.getOrderId()).orElseThrow();
        order.markReserved();
        orderRepository.save(order);

        // Assert - verify persisted (COMPLIANCE: transactional support)
        Order retrieved = orderRepository.findById(created.getOrderId()).orElseThrow();
        assertEquals(Order.Status.RESERVED, retrieved.getStatus());

        // Act - continue transitions
        retrieved.markPaid();
        orderRepository.save(retrieved);

        // Assert
        Order updated = orderRepository.findById(created.getOrderId()).orElseThrow();
        assertEquals(Order.Status.PAID, updated.getStatus());
    }

    @Test
    void placeOrderWithInvalidQuantityThrowsException() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(UUID.randomUUID(), "SKU-001", 0));
    }

    @Test
    void placeOrderWithNullCustomerThrowsException() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(null, "SKU-001", 5));
    }
}

