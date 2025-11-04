package com.comp5348.messaging.email;

import com.comp5348.messaging.events.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens to email_queue and processes email notification events.
 *
 * IMPORTANT: This listener does NOT send emails directly.
 * Instead, it writes to the Outbox table for reliable delivery.
 *
 * Why Outbox Pattern?
 * - If we send email directly and fail, the message is lost
 * - If we write to Outbox first, we can retry later
 * - OutboxWorker processes the Outbox table every 5 seconds
 *
 * Event Flow:
 * 1. OrderOrchestrator publishes event to email_queue
 * 2. This listener receives the event
 * 3. This listener writes to Outbox table (NOT sending email yet)
 * 4. OutboxWorker picks up the Outbox record
 * 5. OutboxWorker calls Email service via HTTP
 * 6. OutboxWorker marks as sent
 *
 * Compliance: §79-80 (Reliable asynchronous messaging), §77 (Data integrity)
 */
@Component
public class EmailMessageListener {

    private static final Logger log = LoggerFactory.getLogger(EmailMessageListener.class);

    // TODO: Inject OutboxRepository when Outbox infrastructure is created
    // private final OutboxRepository outboxRepository;

    public EmailMessageListener() {
        // TODO: Add OutboxRepository constructor injection
    }

    /**
     * Process email notification events from email_queue.
     *
     * This listener writes events to Outbox table instead of sending emails directly.
     * This ensures reliable delivery even if Email service is temporarily unavailable.
     *
     * @param event The email notification event from RabbitMQ
     */
    @RabbitListener(queues = "email_queue")
    public void onMessage(EventMessage event) {
        try {
            String correlationId = event.getCorrelationId() != null ? event.getCorrelationId() : "N/A";

            log.info("[Email] 📧 Received email event: {} for order {} | To: {} | Correlation: {}",
                event.getType(), event.getOrderId(), event.getCustomerEmail(), correlationId);

            // TODO: Implement Outbox pattern
            // 1. Create OutboxEvent from EventMessage
            // 2. Save to Outbox table
            // 3. OutboxWorker will process it later

            /*
            OutboxEvent outboxEvent = new OutboxEvent(
                event.getOrderId(),
                event.getType(),
                event.getCustomerEmail(),
                event.getDescription(),
                false  // not sent yet
            );
            outboxEvent.setCorrelationId(correlationId);
            outboxEvent.setRetryCount(0);
            outboxEvent.setCreatedAt(LocalDateTime.now());

            outboxRepository.save(outboxEvent);
            log.info("[Email] ✅ Email event queued to Outbox for order {}", event.getOrderId());
            */

            // TEMPORARY: Log instead of sending (until Outbox is implemented)
            log.info("[Email] [TEMP] Would send email to {} | Subject: {} | Message: {}",
                event.getCustomerEmail(), event.getType(), event.getDescription());

        } catch (Exception e) {
            log.error("[Email] Error processing email event: {} for order {}. Correlation ID: {}. Error: {}",
                event.getType(), event.getOrderId(), event.getCorrelationId(), e.getMessage(), e);
            // Exception will trigger DLQ retry logic
            throw new RuntimeException("Failed to process email event", e);
        }
    }
}
