package com.comp5348.store.order.infrastructure.persistence;

import com.comp5348.store.order.domain.model.OrderSagaState;
import com.comp5348.store.order.domain.repository.OrderSagaStateRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySagaStateRepository implements OrderSagaStateRepository {

    private final Map<UUID, OrderSagaState> storage = new ConcurrentHashMap<>();

    @Override
    public void save(OrderSagaState state) {
        storage.put(state.getOrderId(), state);
    }

    @Override
    public Optional<OrderSagaState> findById(UUID orderId) {
        return Optional.ofNullable(storage.get(orderId));
    }

    @Override
    public void delete(UUID orderId) {
        storage.remove(orderId);
    }
}
