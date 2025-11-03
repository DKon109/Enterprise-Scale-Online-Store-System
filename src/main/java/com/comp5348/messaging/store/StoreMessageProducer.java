package com.comp5348.messaging.store;

import com.comp5348.messaging.config.RabbitMQConfig;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class StoreMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    public StoreMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendPaymentEvent(Long transactionID, Long orderId, Double amount, String type, String status, Long bankReferenceID) {
        JSONObject json = new JSONObject();
        json.put("transactionID", transactionID);
        json.put("orderId", orderId);
        json.put("amount", amount);
        json.put("type", type);
        json.put("status", status);
        json.put("bankReferenceID", bankReferenceID);
        json.put("timestamp", Instant.now().toString());

        rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, json.toString());
    }
}
