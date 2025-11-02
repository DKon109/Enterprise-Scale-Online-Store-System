package com.comp5348.store.order.application.service;

import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.domain.model.OrderTimelineEntry;
import com.comp5348.store.order.domain.repository.OrderEventRepository;
import com.comp5348.store.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

public class OrderQueryService {

    public record TimelineEvent(String event, Instant at, Map<String, Object> payload) { }

    public record OrderSnapshot(
            UUID orderId,
            String customerId,
            String itemId,
            int quantity,
            Order.Status status,
            Instant createdAt,
            Instant updatedAt,
            List<TimelineEvent> timeline) { }

    private final OrderRepository orders;
    private final OrderEventRepository orderEvents;

    public OrderQueryService(OrderRepository orders, OrderEventRepository orderEvents) {
        this.orders = orders;
        this.orderEvents = orderEvents;
    }

    public OrderSnapshot getById(UUID orderId) {
        Order order = orders.getRequired(orderId);
        List<TimelineEvent> timeline = orderEvents.findByOrderId(orderId)
                .stream()
                .map(this::toTimelineEvent)
                .collect(Collectors.toList());
        return new OrderSnapshot(
                order.getOrderId(),
                order.getCustomerId(),
                order.getItemId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                timeline);
    }

    private TimelineEvent toTimelineEvent(OrderTimelineEntry entry) {
        return new TimelineEvent(entry.getEventType(), entry.getOccurredAt(), entry.getPayload());
    }
}
