package com.comp5348.messaging.bank;

import com.comp5348.messaging.events.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.comp5348.bank.service.PaymentTransactionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BankMessageListener {

    private static final Logger log = LoggerFactory.getLogger(BankMessageListener.class);
    private final PaymentTransactionService paymentTransactionService;

    public BankMessageListener(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }

    @RabbitListener(queues = "bank_queue")
    public void onMessage(EventMessage event) {
        switch (event.getType()) {
            case "payment.success" -> log.info("💰 [Bank] Processed payment for order {}", event.getOrderId());
            case "payment.failed" -> log.warn("⚠️ [Bank] Payment failed for order {}", event.getOrderId());
            case "refund.completed" -> log.info("💸 [Bank] Refunded customer for order {}", event.getOrderId());
            default -> log.debug("🌀 [Bank] Unknown event: {}", event);
        }
    }
}
