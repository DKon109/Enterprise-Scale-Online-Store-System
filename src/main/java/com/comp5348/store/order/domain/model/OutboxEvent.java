package com.comp5348.store.order.domain.model;

import java.time.Instant;
import java.util.UUID;

public class OutboxEvent {

    private final long id;
    private final UUID aggregateId;
    private final String type;
    private final String payload;
    private final boolean published;
    private final Instant createdAt;

    public OutboxEvent(long id, UUID aggregateId, String type, String payload, boolean published, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.published = published;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OutboxEvent markPublished() {
        return new OutboxEvent(id, aggregateId, type, payload, true, createdAt);
    }
}
