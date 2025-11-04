package com.comp5348.messaging.email;

import com.comp5348.messaging.events.EventMessage;
import com.comp5348.store.order.model.OutboxEvent;
import com.comp5348.store.order.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens to email_queue and persists notification events in the Outbox table.
 *
 * OutboxWorker later delivers them to the Email service, ensuring reliable dispatch.
 */
@Component
public class EmailMessageListener {

    private static final Logger log = LoggerFactory.getLogger(EmailMessageListener.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EmailMessageListener(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "email_queue")
    public void onMessage(EventMessage event) {
        String correlationId = event.getCorrelationId() != null ? event.getCorrelationId() : "N/A";
        log.info("[Email] 📧 Received email event: {} for order {} | To: {} | Correlation: {}",
                event.getType(), event.getOrderId(), event.getCustomerEmail(), correlationId);

        try {
            String payload = buildPayload(event, correlationId);
            OutboxEvent outboxEvent = new OutboxEvent(event.getOrderId(), event.getType(), payload);
            outboxRepository.save(outboxEvent);
            log.info("[Email] ✅ Email event queued to Outbox for order {}", event.getOrderId());
        } catch (Exception e) {
            log.error("[Email] Error processing email event: {} for order {}. Correlation ID: {}. Error: {}",
                    event.getType(), event.getOrderId(), correlationId, e.getMessage(), e);
            throw new RuntimeException("Failed to process email event", e);
        }
    }

    private String buildPayload(EventMessage event, String correlationId) throws JsonProcessingException {
        Map<String, String> payload = new HashMap<>();
        if (event.getCustomerEmail() != null) {
            payload.put("customerEmail", event.getCustomerEmail());
        }
        if (event.getDescription() != null) {
            payload.put("description", event.getDescription());
        }
        payload.put("correlationId", correlationId);
        return payload.isEmpty() ? "{}" : objectMapper.writeValueAsString(payload);
    }
}
