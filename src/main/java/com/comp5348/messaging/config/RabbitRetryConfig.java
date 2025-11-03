package com.comp5348.messaging.config;

import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitRetryConfig {

    // Optional: custom recoverer that logs failed messages instead of printing a stack trace
    @Bean
    public MessageRecoverer messageRecoverer() {
        return (message, cause) -> {
            System.out.println("[DLQ] Moving message to DLQ after retries exhausted: "
                    + new String(message.getBody()));
        };
    }
}
