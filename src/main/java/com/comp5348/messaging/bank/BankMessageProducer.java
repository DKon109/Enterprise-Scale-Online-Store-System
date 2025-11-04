package com.comp5348.messaging.bank;

import com.comp5348.bank.model.PaymentTransaction;
import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.messaging.events.EventMessage;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes bank transaction outcomes to RabbitMQ for downstream consumers.
 *
 * Compliance: §79-80 (reliable messaging), §242 (idempotency metadata), §246 (correlation tracking)
 */
@Component
public class BankMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(BankMessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public BankMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publish a transaction event once a payment or refund is confirmed.
     *
     * @param transaction persisted bank transaction
     * @param eventType   event type (e.g. payment.success, refund.completed)
     */
    public void publishTransactionEvent(PaymentTransaction transaction, String eventType) {
        EventMessage message = new EventMessage();
        message.setType(eventType);
        message.setOrderId(transaction.getOrderID());
        message.setAmount(transaction.getAmount());
        message.setDescription("Bank transaction %s".formatted(transaction.getStatus()));
        message.setCorrelationId(transaction.getCorrelationId());
        message.setIdempotencyKey(transaction.getIdempotencyKey());
        message.setTimestamp(LocalDateTime.now());
        message.setRetryCount(0);

        rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, message);

        log.debug(
                "[BankProducer] Published event {} for order {} (idempotency={}, correlation={})",
                eventType,
                transaction.getOrderID(),
                transaction.getIdempotencyKey(),
                transaction.getCorrelationId());
    }
}
