package com.comp5348.bank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.comp5348.bank.repository.PaymentTransactionRepository;
import com.comp5348.messaging.bank.BankMessageProducer;
import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.messaging.events.EventMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({PaymentTransactionService.class, BankMessageProducer.class})
@ActiveProfiles("test")
class PaymentTransactionServiceMessagingIntegrationTest {

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void createPurchaseTransactionPublishesEvent() {
        UUID orderId = UUID.randomUUID();

        paymentTransactionService.createPurchaseTransaction(orderId, 50.0, "integration-key-1", "corr-int-1");

        ArgumentCaptor<EventMessage> captor = ArgumentCaptor.forClass(EventMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.BANK_QUEUE), captor.capture());

        EventMessage message = captor.getValue();
        assertEquals("payment.success", message.getType());
        assertEquals(orderId, message.getOrderId());
        assertEquals("integration-key-1", message.getIdempotencyKey());
        assertEquals("corr-int-1", message.getCorrelationId());
        assertNotNull(message.getTimestamp());

        // Ensure transaction persisted
        assertTrue(paymentTransactionRepository.findByIdempotencyKey("integration-key-1").isPresent());
    }
}
