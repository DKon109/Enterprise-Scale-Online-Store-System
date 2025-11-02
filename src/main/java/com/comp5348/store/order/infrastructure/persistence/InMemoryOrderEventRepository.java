package com.comp5348.store.order.infrastructure.persistence;

import com.comp5348.store.order.domain.model.OrderTimelineEntry;
import com.comp5348.store.order.domain.repository.OrderEventRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryOrderEventRepository implements OrderEventRepository {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<OrderTimelineEntry>> storage = new ConcurrentHashMap<>();

    @Override
    public void record(OrderTimelineEntry entry) {
        storage.computeIfAbsent(entry.getOrderId(), ignored -> new CopyOnWriteArrayList<>()).add(entry);
    }

    @Override
    public List<OrderTimelineEntry> findByOrderId(UUID orderId) {
        return storage.containsKey(orderId)
                ? Collections.unmodifiableList(new ArrayList<>(storage.get(orderId)))
                : List.of();
    }
}
