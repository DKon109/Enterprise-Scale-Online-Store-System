package com.comp5348.store.order.domain.model;

import java.time.Instant;
import java.util.UUID;

public class OrderSagaState {

    private final UUID orderId;
    private final String step;
    private final int retries;
    private final String lastError;
    private final Instant updatedAt;

    public OrderSagaState(UUID orderId, String step, int retries, String lastError, Instant updatedAt) {
        this.orderId = orderId;
        this.step = step;
        this.retries = retries;
        this.lastError = lastError;
        this.updatedAt = updatedAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getStep() {
        return step;
    }

    public int getRetries() {
        return retries;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public OrderSagaState advance(String newStep) {
        return new OrderSagaState(orderId, newStep, 0, null, Instant.now());
    }

    public OrderSagaState retry(String error, int nextRetryCount) {
        return new OrderSagaState(orderId, step, nextRetryCount, error, Instant.now());
    }
}
