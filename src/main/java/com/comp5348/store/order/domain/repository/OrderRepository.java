package com.comp5348.store.order.domain.repository;

import com.comp5348.store.order.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(UUID orderId);

    default Order getRequired(UUID orderId) {
        return findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order %s not found".formatted(orderId)));
    }
}
