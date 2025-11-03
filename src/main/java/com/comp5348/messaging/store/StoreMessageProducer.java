package com.comp5348.messaging.store;

import com.comp5348.messaging.events.EventMessage;
import com.comp5348.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class StoreMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(StoreMessageProducer.class);
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StoreMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishEvent(EventMessage event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, json);
            log.info(" [StoreProducer] Published event '{}' to queue '{}'", event.getType(), RabbitMQConfig.BANK_QUEUE);
        } catch (Exception e) {
            log.error(" [StoreProducer] Failed to publish event '{}': {}", event.getType(), e.getMessage());
            throw new RuntimeException("Failed to serialize or publish EventMessage", e);
        }
    }
}
