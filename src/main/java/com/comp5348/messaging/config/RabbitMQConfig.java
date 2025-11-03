package com.comp5348.messaging.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String BANK_QUEUE = "bank_queue";
    public static final String BANK_QUEUE_DLQ = "bank_queue_dlq";

    // Main queue (with dead-letter configuration)
    @Bean
    public Queue bankQueue() {
        return QueueBuilder.durable(BANK_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", BANK_QUEUE_DLQ)
                .build();
    }

    // Dead Letter Queue (DLQ)
    @Bean
    public Queue bankQueueDlq() {
        return QueueBuilder.durable(BANK_QUEUE_DLQ).build();
    }
}
