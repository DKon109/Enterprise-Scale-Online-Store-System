package com.comp5348.store.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.comp5348.store.order.exception.OrderNotFoundException;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for OrderService.
 *
 * Tests business logic, validation, and repository interactions.
 * Uses mocks to isolate the service layer from persistence.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void placeOrderSuccessfully() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        String itemId = "SKU-001";
        int quantity = 5;

        Order savedOrder = new Order(UUID.randomUUID(), customerId, itemId, quantity);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        Order result = orderService.placeOrder(customerId, itemId, quantity);

        // Assert
        assertNotNull(result);
        assertEquals(customerId, result.getCustomerId());
        assertEquals(itemId, result.getItemId());
        assertEquals(quantity, result.getQuantity());
        assertEquals(Order.Status.PENDING, result.getStatus());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void placeOrderWithNullCustomerThrowsException() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(null, "SKU-001", 5));
    }

    @Test
    void placeOrderWithBlankItemIdThrowsException() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(UUID.randomUUID(), "", 5));
    }

    @Test
    void placeOrderWithZeroQuantityThrowsException() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(UUID.randomUUID(), "SKU-001", 0));
    }

    @Test
    void placeOrderWithNegativeQuantityThrowsException() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(UUID.randomUUID(), "SKU-001", -5));
    }

    @Test
    void getOrderSuccessfully() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Act
        Order result = orderService.getOrder(orderId);

        // Assert
        assertEquals(orderId, result.getOrderId());
        assertEquals(customerId, result.getCustomerId());
        verify(orderRepository).findById(orderId);
    }

    @Test
    void getOrderNotFoundThrowsException() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(orderId));
    }

    @Test
    void getOrderWithNullIdThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> orderService.getOrder(null));
    }

    @Test
    void getCustomerOrdersSuccessfully() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Order order1 = new Order(UUID.randomUUID(), customerId, "SKU-001", 2);
        Order order2 = new Order(UUID.randomUUID(), customerId, "SKU-002", 3);

        when(orderRepository.findByCustomerId(customerId)).thenReturn(List.of(order1, order2));

        // Act
        List<Order> result = orderService.getCustomerOrders(customerId);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(o -> o.getCustomerId().equals(customerId)));
        verify(orderRepository).findByCustomerId(customerId);
    }

    @Test
    void getCustomerOrdersWithNullIdThrowsException() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class, () -> orderService.getCustomerOrders(null));
    }

    @Test
    void cancelOrderSuccessfully() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // Act
        orderService.cancelOrder(orderId);

        // Assert
        assertEquals(Order.Status.CANCELLED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrderAfterShipmentThrowsException() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);
        order.markReserved();
        order.markPaid();
        order.markShipmentRequested();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    void cancelOrderWithNullIdThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> orderService.cancelOrder(null));
    }

    @Test
    void orderExistsReturnsTrue() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(true);

        // Act
        boolean result = orderService.orderExists(orderId);

        // Assert
        assertTrue(result);
        verify(orderRepository).existsById(orderId);
    }

    @Test
    void orderExistsReturnsFalse() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(false);

        // Act
        boolean result = orderService.orderExists(orderId);

        // Assert
        assertFalse(result);
    }

    @Test
    void orderExistsWithNullIdReturnsFalse() {
        // Act
        boolean result = orderService.orderExists(null);

        // Assert
        assertFalse(result);
    }
}

