package com.comp5348.store.order.infrastructure.outbox;

import com.comp5348.store.order.application.event.IntegrationEvent;
import com.comp5348.store.order.application.event.OutboxPublisher;
import com.comp5348.store.order.domain.repository.OutboxEventRepository;

public class PersistentOutboxPublisher implements OutboxPublisher {

    private final OutboxEventRepository repository;

    public PersistentOutboxPublisher(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(IntegrationEvent event) {
        repository.append(event.aggregateId(), event.type(), event.payload());
    }
}
