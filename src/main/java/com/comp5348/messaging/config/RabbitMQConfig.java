package com.comp5348.messaging.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ===== Queue names =====
    public static final String BANK_QUEUE = "bank_queue";
    public static final String BANK_QUEUE_DLQ = "bank_queue_dlq";

    public static final String WAREHOUSE_QUEUE = "warehouse_queue";
    public static final String WAREHOUSE_QUEUE_DLQ = "warehouse_queue_dlq";

    public static final String EMAIL_QUEUE = "email_queue";
    public static final String EMAIL_QUEUE_DLQ = "email_queue_dlq";

    // ===== BANK =====
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

    // ===== WAREHOUSE =====
    @Bean
    public Queue warehouseQueue() {
        return QueueBuilder.durable(WAREHOUSE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", WAREHOUSE_QUEUE_DLQ)
                .build();
    }

    @Bean
    public Queue warehouseQueueDlq() {
        return QueueBuilder.durable(WAREHOUSE_QUEUE_DLQ).build();
    }

    // ===== EMAIL =====
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", EMAIL_QUEUE_DLQ)
                .build();
    }

    @Bean
    public Queue emailQueueDlq() {
        return QueueBuilder.durable(EMAIL_QUEUE_DLQ).build();
    }

    // ===== Converter =====
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Note: RabbitTemplate is auto-configured by Spring Boot
    // We only need to configure the MessageConverter
}
