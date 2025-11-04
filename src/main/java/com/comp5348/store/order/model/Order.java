package com.comp5348.store.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Order Aggregate Root - Represents a customer order with state machine transitions.
 *
 * State Machine:
 *   PENDING → RESERVED → PAID → SHIPMENT_REQUESTED → DELIVERED
 *                                                  ↘ CANCELLED
 *
 * Invariants:
 * - quantity must be > 0
 * - customerId must not be null
 * - itemId must not be null
 * - State transitions must follow the state machine
 */
@Entity
@Table(name = "orders")
public class Order {

    public enum Status {
        PENDING,
        RESERVED,
        PAID,
        SHIPMENT_REQUESTED,
        DELIVERED,
        CANCELLED
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false, updatable = false)
    private String itemId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(nullable = false)
    private String status;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "request_id", length = 64, unique = true)
    private String requestId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Order() {
        // For JPA
    }

    public Order(UUID orderId, UUID customerId, String itemId, int quantity) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        this.quantity = quantity;
        this.status = Status.PENDING.name();

        // Initialize timestamps for non-JPA usage
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // State Transitions

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

    /**
     * Record a shipment failure while keeping the order in PAID status.
     * Updates the timestamp so downstream consumers can detect the change.
     */
    public void markShipmentFailed() {
        ensureStatus(Status.PAID);
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (!canCancel()) {
            throw new IllegalStateException("Order cannot be cancelled from status " + status);
        }
        transitionTo(Status.CANCELLED);
    }

    public boolean canCancel() {
        Status currentStatus = getStatus();
        return currentStatus == Status.PENDING
                || currentStatus == Status.RESERVED
                || currentStatus == Status.PAID;
    }

    // Helper Methods

    private void ensureStatus(Status expected) {
        if (getStatus() != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    private void transitionTo(Status next) {
        this.status = Objects.requireNonNull(next, "next status").name();
        this.updatedAt = Instant.now();
    }

    // Getters

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Status getStatus() {
        return Status.valueOf(status);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = normalise(correlationId);
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = normalise(requestId);
    }

    private String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
