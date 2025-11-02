package com.comp5348.store.order.domain.repository;

import com.comp5348.store.order.domain.model.OutboxEvent;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {

    OutboxEvent append(UUID aggregateId, String type, String payload);

    List<OutboxEvent> findUnpublished(int batchSize);

    void markPublished(long eventId);
}
