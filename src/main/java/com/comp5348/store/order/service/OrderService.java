package com.comp5348.store.order.service;

import com.comp5348.store.order.exception.OrderNotFoundException;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.repository.OrderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for Order operations.
 *
 * Responsibilities:
 * - Validate business rules
 * - Orchestrate repository operations
 * - Handle transactional boundaries
 * - Provide use case methods
 *
 * This service layer sits between the presentation layer (controllers) and the
 * persistence layer (repository). It encapsulates business logic and ensures
 * data consistency.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Place a new order.
     *
     * Creates a new Order domain object and persists it to the database.
     * The order starts in PENDING status.
     *
     * @param customerId the customer placing the order
     * @param itemId the item being ordered
     * @param quantity the quantity ordered
     * @return the created order
     * @throws IllegalArgumentException if quantity is invalid
     */
    @Transactional
    public Order placeOrder(UUID customerId, String itemId, int quantity) {
        // Validate inputs
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        // Create domain object
        Order order = new Order(UUID.randomUUID(), customerId, itemId, quantity);

        // Persist to database
        return orderRepository.save(order);
    }

    /**
     * Retrieve an order by ID.
     *
     * @param orderId the order ID
     * @return the order
     * @throws OrderNotFoundException if order not found
     */
    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Retrieve all orders for a customer.
     *
     * @param customerId the customer ID
     * @return list of orders for the customer
     */
    @Transactional(readOnly = true)
    public List<Order> getCustomerOrders(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }

        return orderRepository.findByCustomerId(customerId);
    }

    /**
     * Cancel an order.
     *
     * Only orders in PENDING, RESERVED, or PAID status can be cancelled.
     * Once an order is in SHIPMENT_REQUESTED or later, it cannot be cancelled.
     *
     * @param orderId the order ID
     * @throws OrderNotFoundException if order not found
     * @throws IllegalStateException if order cannot be cancelled from current status
     */
    @Transactional
    public void cancelOrder(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        Order order = getOrder(orderId);

        if (!order.canCancel()) {
            throw new IllegalStateException(
                    "Order cannot be cancelled from status " + order.getStatus());
        }

        order.cancel();
        orderRepository.save(order);
    }

    /**
     * Check if an order exists.
     *
     * @param orderId the order ID
     * @return true if order exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean orderExists(UUID orderId) {
        if (orderId == null) {
            return false;
        }
        return orderRepository.existsById(orderId);
    }
}
