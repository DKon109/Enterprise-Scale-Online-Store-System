package com.comp5348.messaging.warehouse;

import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.messaging.events.EventMessage;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes warehouse/delivery lifecycle events to RabbitMQ so the store service
 * can react asynchronously (e.g. update order status when DeliveryCo sends webhooks).
 */
@Component
public class WarehouseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WarehouseEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    @Value("${app.messaging.enabled:true}")
    private boolean messagingEnabled = true;

    public WarehouseEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publish a delivery status update to the warehouse queue.
     *
     * @param orderId       order identifier
     * @param eventType     message type (e.g. item.delivered)
     * @param description   human readable description for logs/notifications
     * @param correlationId correlation id propagated from inbound webhook (optional)
     * @param idempotencyKey idempotency key to avoid duplicate processing (optional)
     */
    public void publishDeliveryStatus(UUID orderId,
                                      String eventType,
                                      String description,
                                      String correlationId,
                                      String idempotencyKey) {
        if (!messagingEnabled) {
            log.info("[WarehousePublisher] Demo mode: recorded {} for order {} without RabbitMQ",
                    eventType, orderId);
            return;
        }
        EventMessage message = new EventMessage();
        message.setType(eventType);
        message.setOrderId(orderId);
        message.setDescription(description);
        message.setCorrelationId(normalise(correlationId));
        message.setIdempotencyKey(normalise(idempotencyKey));
        message.setTimestamp(LocalDateTime.now());
        message.setRetryCount(0);

        rabbitTemplate.convertAndSend(RabbitMQConfig.WAREHOUSE_QUEUE, message);
        log.info("[WarehousePublisher] Published {} for order {} (correlation={}, idempotencyKey={})",
                eventType, orderId, message.getCorrelationId(), message.getIdempotencyKey());
    }

    private String normalise(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
