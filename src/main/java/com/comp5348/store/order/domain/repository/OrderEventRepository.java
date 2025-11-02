package com.comp5348.store.order.domain.repository;

import com.comp5348.store.order.domain.model.OrderTimelineEntry;
import java.util.List;
import java.util.UUID;

public interface OrderEventRepository {

    void record(OrderTimelineEntry entry);

    List<OrderTimelineEntry> findByOrderId(UUID orderId);
}
