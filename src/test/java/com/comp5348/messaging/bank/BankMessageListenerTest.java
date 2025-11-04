package com.comp5348.messaging.bank;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.comp5348.bank.service.PaymentTransactionService;
import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.messaging.events.EventMessage;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class BankMessageListenerTest {

    private PaymentTransactionService paymentTransactionService;
    private RabbitTemplate rabbitTemplate;
    private BankMessageListener listener;

    @BeforeEach
    void setUp() {
        paymentTransactionService = mock(PaymentTransactionService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        listener = new BankMessageListener(paymentTransactionService, rabbitTemplate);
    }

    @Test
    void handlesPaymentSuccessEventWithoutCallingService() {
        EventMessage event = new EventMessage(
                "payment.success",
                UUID.randomUUID(),
                100.0,
                "customer@example.com",
                "Payment authorised",
                "corr-1",
                "idemp-1",
                java.time.LocalDateTime.now(),
                0);

        assertDoesNotThrow(() -> listener.onMessage(event));
        verifyNoInteractions(paymentTransactionService);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void handlesPaymentFailedEvent() {
        EventMessage event = new EventMessage(
                "payment.failed",
                UUID.randomUUID(),
                100.0,
                "customer@example.com",
                "Payment declined",
                "corr-2",
                "idemp-2",
                java.time.LocalDateTime.now(),
                0);

        assertDoesNotThrow(() -> listener.onMessage(event));
        verifyNoInteractions(paymentTransactionService);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void handlesRefundCompletedEvent() {
        EventMessage event = new EventMessage(
                "refund.completed",
                UUID.randomUUID(),
                75.0,
                "customer@example.com",
                "Refund processed",
                "corr-3",
                "idemp-3",
                java.time.LocalDateTime.now(),
                0);

        assertDoesNotThrow(() -> listener.onMessage(event));
        verifyNoInteractions(paymentTransactionService);
        ArgumentCaptor<EventMessage> messageCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EMAIL_QUEUE), messageCaptor.capture());
        EventMessage forwarded = messageCaptor.getValue();
        assertEquals("REFUND_COMPLETED", forwarded.getType());
        assertEquals(event.getOrderId(), forwarded.getOrderId());
        assertEquals(event.getCorrelationId(), forwarded.getCorrelationId());
        assertNotNull(forwarded.getTimestamp());
    }

    @Test
    void handlesUnknownEventTypeGracefully() {
        EventMessage event = new EventMessage(
                "unknown.event",
                UUID.randomUUID(),
                null,
                null,
                "Unknown event",
                null,
                null,
                java.time.LocalDateTime.now(),
                0);

        assertDoesNotThrow(() -> listener.onMessage(event));
        verifyNoInteractions(paymentTransactionService);
        verifyNoInteractions(rabbitTemplate);
    }
}
