package com.comp5348.store.order.model;

/**
 * Lifecycle state of an outbox event.
 *
 * PENDING  - Awaiting delivery attempts.
 * SENT     - Successfully dispatched to the downstream channel.
 * FAILED   - Exhausted retries; manual intervention required.
 */
public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
