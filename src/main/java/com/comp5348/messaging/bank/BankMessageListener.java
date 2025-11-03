package com.comp5348.messaging.bank;

import com.comp5348.bank.service.PaymentTransactionService;
import com.comp5348.messaging.config.RabbitMQConfig;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BankMessageListener {

    private final PaymentTransactionService paymentTransactionService;

    public BankMessageListener(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }

    @RabbitListener(queues = RabbitMQConfig.BANK_QUEUE)
    public void onMessage(String message) {
        System.out.println("[BankMessageListener] Received: " + message);
        JSONObject json = new JSONObject(message);

        String type = json.optString("type", "");
        UUID orderId = UUID.fromString(json.getString("orderId"));
        Double amount = json.getDouble("amount");

        switch (type.toUpperCase()) {
            case "CHARGE_PAYMENT" -> paymentTransactionService.createPurchaseTransaction(orderId, amount);
            case "DELIVERY_FAILED" -> paymentTransactionService.createRefundTransaction(orderId, amount);
            default -> System.out.println("[BankMessageListener] Unknown event: " + type);
        }
    }
}
