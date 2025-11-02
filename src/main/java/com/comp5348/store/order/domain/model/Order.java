package com.comp5348.store.order.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing the lifecycle of a customer order.
 */
public class Order {

    public enum Status {
        PENDING,
        RESERVED,
        PAID,
        SHIPMENT_REQUESTED,
        DELIVERED,
        CANCELLED
    }

    private final UUID orderId;
    private final String customerId;
    private final String itemId;
    private final int quantity;
    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Order(UUID orderId, String customerId, String itemId, int quantity) {
        this(orderId, customerId, itemId, quantity, Status.PENDING, Instant.now(), Instant.now());
    }

    private Order(UUID orderId, String customerId, String itemId, int quantity, Status status, Instant createdAt, Instant updatedAt) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required");
        }

        this.orderId = orderId;
        this.customerId = customerId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void markReserved() {
        ensureStatus(Status.PENDING);
        transitionTo(Status.RESERVED);
    }

    public void markPaid() {
        ensureStatus(Status.RESERVED);
        transitionTo(Status.PAID);
    }

    public void markShipmentRequested() {
        ensureStatus(Status.PAID);
        transitionTo(Status.SHIPMENT_REQUESTED);
    }

    public void markDelivered() {
        ensureStatus(Status.SHIPMENT_REQUESTED);
        transitionTo(Status.DELIVERED);
    }

    public void cancel() {
        if (!canCancel()) {
            throw new IllegalStateException("Order cannot be cancelled from status " + status);
        }
        transitionTo(Status.CANCELLED);
    }

    public boolean canCancel() {
        return status == Status.PENDING
                || status == Status.RESERVED
                || status == Status.PAID;
    }

    private void ensureStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    private void transitionTo(Status next) {
        this.status = Objects.requireNonNull(next, "next status");
        this.updatedAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static Order rehydrate(
            UUID orderId,
            String customerId,
            String itemId,
            int quantity,
            Status status,
            Instant createdAt,
            Instant updatedAt) {
        return new Order(orderId, customerId, itemId, quantity, status, createdAt, updatedAt);
    }
}
