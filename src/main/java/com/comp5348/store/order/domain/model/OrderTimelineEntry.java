package com.comp5348.store.order.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class OrderTimelineEntry {

    private final UUID orderId;
    private final String eventType;
    private final Map<String, Object> payload;
    private final Instant occurredAt;

    public OrderTimelineEntry(UUID orderId, String eventType, Map<String, Object> payload, Instant occurredAt) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
        this.occurredAt = occurredAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
