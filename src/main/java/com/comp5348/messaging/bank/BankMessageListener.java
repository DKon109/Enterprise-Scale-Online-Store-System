package com.comp5348.messaging.bank;

import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.messaging.events.EventMessage;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.comp5348.bank.service.PaymentTransactionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Listens to bank_queue and processes payment-related events.
 *
 * This listener reacts to events published by OrderOrchestrator AFTER payment operations.
 * It does NOT make HTTP calls to Bank service (that's done synchronously in OrderOrchestrator).
 * Instead, it logs payment status and updates internal state.
 *
 * Compliance: §79-80 (Reliable asynchronous messaging)
 */
@Component
public class BankMessageListener {

    private static final Logger log = LoggerFactory.getLogger(BankMessageListener.class);
    private final PaymentTransactionService paymentTransactionService;
    private final RabbitTemplate rabbitTemplate;

    public BankMessageListener(PaymentTransactionService paymentTransactionService, RabbitTemplate rabbitTemplate) {
        this.paymentTransactionService = paymentTransactionService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Process payment events from bank_queue.
     *
     * Event Flow:
     * 1. OrderOrchestrator calls Bank service synchronously (HTTP)
     * 2. OrderOrchestrator publishes event to bank_queue
     * 3. This listener receives the event asynchronously
     * 4. This listener logs/processes the payment status
     *
     * @param event The payment event from RabbitMQ
     */
    @RabbitListener(queues = "bank_queue")
    public void onMessage(EventMessage event) {
        try {
            String correlationId = event.getCorrelationId() != null ? event.getCorrelationId() : "N/A";

            switch (event.getType()) {
                case "payment.success" -> handlePaymentSuccess(event, correlationId);
                case "payment.failed" -> handlePaymentFailure(event, correlationId);
                case "refund.completed" -> handleRefundCompleted(event, correlationId);
                default -> log.debug("[Bank] Unknown event type: {} for order {}", event.getType(), event.getOrderId());
            }
        } catch (Exception e) {
            log.error("[Bank] Error processing event: {} for order {}. Correlation ID: {}. Error: {}",
                event.getType(), event.getOrderId(), event.getCorrelationId(), e.getMessage(), e);
            // Exception will trigger DLQ retry logic
            throw new RuntimeException("Failed to process bank event", e);
        }
    }

    /**
     * Handle successful payment event.
     *
     * At this point, the Bank service has already processed the payment synchronously.
     * This listener just logs the status for audit trail.
     */
    private void handlePaymentSuccess(EventMessage event, String correlationId) {
        log.info("[Bank] ✅ Payment SUCCESS for order {} | Amount: {} | Correlation: {}",
            event.getOrderId(), event.getAmount(), correlationId);

        // Optional: Update internal state, metrics, or audit log
        // Example: paymentTransactionService.recordPaymentSuccess(event.getOrderId());
    }

    /**
     * Handle failed payment event.
     *
     * At this point, the Bank service has already rejected the payment synchronously.
     * This listener logs the failure for audit trail and monitoring.
     */
    private void handlePaymentFailure(EventMessage event, String correlationId) {
        log.warn("[Bank] ❌ Payment FAILED for order {} | Correlation: {}",
            event.getOrderId(), correlationId);

        // Optional: Update internal state, trigger alerts, or audit log
        // Example: paymentTransactionService.recordPaymentFailure(event.getOrderId());
    }

    /**
     * Handle refund completed event.
     *
     * At this point, the Bank service has already processed the refund synchronously.
     * This listener logs the refund for audit trail.
     */
    private void handleRefundCompleted(EventMessage event, String correlationId) {
        log.info("[Bank] 💸 Refund COMPLETED for order {} | Amount: {} | Correlation: {}",
            event.getOrderId(), event.getAmount(), correlationId);

        EventMessage notificationEvent = new EventMessage();
        notificationEvent.setType("REFUND_COMPLETED");
        notificationEvent.setOrderId(event.getOrderId());
        notificationEvent.setAmount(event.getAmount());
        notificationEvent.setDescription(resolveRefundDescription(event));
        notificationEvent.setCorrelationId(correlationId);
        notificationEvent.setIdempotencyKey(event.getIdempotencyKey());
        notificationEvent.setTimestamp(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now());
        notificationEvent.setRetryCount(event.getRetryCount());
        notificationEvent.setCustomerEmail(event.getCustomerEmail());

        rabbitTemplate.convertAndSend(RabbitMQConfig.EMAIL_QUEUE, notificationEvent);
        log.debug("[Bank] Forwarded refund completion for order {} to {}", event.getOrderId(), RabbitMQConfig.EMAIL_QUEUE);
    }

    private String resolveRefundDescription(EventMessage event) {
        if (event.getDescription() != null && !event.getDescription().trim().isEmpty()) {
            return event.getDescription();
        }
        return "Refund completed for order " + event.getOrderId();
    }
}
