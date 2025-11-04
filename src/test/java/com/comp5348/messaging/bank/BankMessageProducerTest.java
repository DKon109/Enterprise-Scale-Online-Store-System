package com.comp5348.messaging.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.comp5348.bank.model.PaymentTransaction;
import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.messaging.events.EventMessage;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class BankMessageProducerTest {

    private RabbitTemplate rabbitTemplate;
    private BankMessageProducer producer;

    @BeforeEach
    void setUp() {
        rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        producer = new BankMessageProducer(rabbitTemplate);
    }

    @Test
    void publishTransactionEventSendsMessageWithMetadata() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction(100.0, LocalDateTime.now(), "Purchase", "Confirmed", orderId);
        transaction.setIdempotencyKey("event-key-1");
        transaction.setCorrelationId("corr-evt-1");

        producer.publishTransactionEvent(transaction, "payment.success");

        ArgumentCaptor<EventMessage> messageCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.BANK_QUEUE), messageCaptor.capture());

        EventMessage message = messageCaptor.getValue();
        assertEquals("payment.success", message.getType());
        assertEquals(orderId, message.getOrderId());
        assertEquals(100.0, message.getAmount());
        assertEquals("event-key-1", message.getIdempotencyKey());
        assertEquals("corr-evt-1", message.getCorrelationId());
        assertNotNull(message.getTimestamp());
    }
}
