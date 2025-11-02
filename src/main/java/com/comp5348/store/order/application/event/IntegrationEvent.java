package com.comp5348.store.order.application.event;

import java.util.UUID;

public record IntegrationEvent(UUID aggregateId, String type, String payload) {
}
