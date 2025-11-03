package com.comp5348.messaging.bank;

import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.bank.mock.MockBankService;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BankMessageListener {

    private final MockBankService mockBankService;

    public BankMessageListener(MockBankService mockBankService) {
        this.mockBankService = mockBankService;
    }

    @RabbitListener(queues = RabbitMQConfig.BANK_QUEUE)
    public void onMessage(String message) {
        System.out.println("[BankMessageListener] Received: " + message);
        JSONObject json = new JSONObject(message);

        String type = json.optString("type", "");
        Long orderId = json.getLong("orderId");
        Double amount = json.getDouble("amount");

        switch (type.toUpperCase()) {
            case "CHARGE_PAYMENT" -> mockBankService.processPayment(orderId, amount);
            case "DELIVERY_FAILED" -> mockBankService.processRefund(orderId, amount);
            default -> System.out.println("[BankMessageListener] Unknown event: " + type);
        }
    }
}
