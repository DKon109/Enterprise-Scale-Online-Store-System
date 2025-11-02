package com.comp5348.store.order.infrastructure.persistence;

import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.domain.repository.OrderRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOrderRepository implements OrderRepository {

    private final Map<UUID, Order> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Order order) {
        storage.put(order.getOrderId(), order);
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return Optional.ofNullable(storage.get(orderId));
    }
}
