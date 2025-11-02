package com.comp5348.store.order.domain.repository;

import com.comp5348.store.order.domain.model.OrderSagaState;
import java.util.Optional;
import java.util.UUID;

public interface OrderSagaStateRepository {

    void save(OrderSagaState state);

    Optional<OrderSagaState> findById(UUID orderId);

    void delete(UUID orderId);
}
