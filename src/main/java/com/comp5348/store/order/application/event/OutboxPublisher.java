package com.comp5348.store.order.application.event;

public interface OutboxPublisher {

    void append(IntegrationEvent event);
}
