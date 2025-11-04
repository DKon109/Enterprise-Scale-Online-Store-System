package com.comp5348.store.order.infrastructure.adapter.notification;

import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.store.order.model.OutboxEvent;
import com.comp5348.store.order.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Notification adapter that persists messages to the Outbox table.
 *
 * OutboxWorker reads the table and delivers the actual notification, ensuring
 * reliable delivery even if the downstream Email service is unavailable.
 */
@Component
public class OutboxNotificationServiceAdapter implements NotificationServicePort {

    private static final Logger log = LoggerFactory.getLogger(OutboxNotificationServiceAdapter.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxNotificationServiceAdapter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(UUID orderId, String template, Map<String, String> variables) {
        String payload = toJson(variables);
        OutboxEvent event = new OutboxEvent(orderId, template, payload);
        outboxRepository.save(event);
        log.info("Queued notification in outbox: orderId={} template={}", orderId, template);
    }

    private String toJson(Map<String, String> variables) {
        try {
            if (variables == null || variables.isEmpty()) {
                return "{}";
            }
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise outbox payload, storing empty payload instead: {}", ex.getMessage());
            return "{}";
        }
    }
}
