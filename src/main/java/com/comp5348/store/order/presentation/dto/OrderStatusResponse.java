package com.comp5348.store.order.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderStatusResponse(
        UUID orderId,
        String status,
        List<TimelineEntry> timeline) {

    public record TimelineEntry(String event, Instant at, Map<String, Object> payload) { }
}
