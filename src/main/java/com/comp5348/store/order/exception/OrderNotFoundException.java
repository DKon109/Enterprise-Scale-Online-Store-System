package com.comp5348.store.order.exception;

import java.util.UUID;

/**
 * Exception thrown when an order is not found in the repository.
 */
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order not found with ID: " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}

