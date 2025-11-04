package com.comp5348.messaging.bank;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.comp5348.bank.service.PaymentTransactionService;
import com.comp5348.messaging.events.EventMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BankMessageListenerTest {

    private final PaymentTransactionService paymentTransactionService = mock(PaymentTransactionService.class);
    private final BankMessageListener listener = new BankMessageListener(paymentTransactionService);

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
    }
}
