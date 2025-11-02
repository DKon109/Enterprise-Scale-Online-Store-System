package com.comp5348.store.order.application.service;

import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.UUID;

public class OrderQueryService {

    public record OrderSnapshot(
            UUID orderId,
            String customerId,
            String itemId,
            int quantity,
            Order.Status status,
            Instant createdAt,
            Instant updatedAt) { }

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    public OrderSnapshot getById(UUID orderId) {
        Order order = orders.getRequired(orderId);
        return new OrderSnapshot(
                order.getOrderId(),
                order.getCustomerId(),
                order.getItemId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
