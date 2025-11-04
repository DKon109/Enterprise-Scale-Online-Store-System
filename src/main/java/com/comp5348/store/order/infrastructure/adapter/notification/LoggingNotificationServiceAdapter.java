package com.comp5348.store.order.infrastructure.adapter.notification;

import com.comp5348.store.order.application.port.NotificationServicePort;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP-based notification adapter that calls the Email service API.
 *
 * Integrates with Email service to send notifications via HTTP.
 * Falls back to logging if Email service is unavailable.
 */
@Component
public class LoggingNotificationServiceAdapter implements NotificationServicePort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationServiceAdapter.class);

    private final RestTemplate restTemplate;
    private final String emailServiceUrl;

    public LoggingNotificationServiceAdapter(RestTemplate restTemplate, @Value("${email.service.url:http://localhost:8082}") String emailServiceUrl) {
        this.restTemplate = restTemplate;
        this.emailServiceUrl = emailServiceUrl;
    }

    @Override
    public void send(UUID orderId, String template, Map<String, String> variables) {
        try {
            // Try to call Email service
            String url = emailServiceUrl + "/send";
            EmailRequest request = new EmailRequest(orderId, template, variables);

            restTemplate.postForObject(url, request, Void.class);
            log.info("Notification sent via Email service: template={} orderId={}", template, orderId);
        } catch (RestClientException e) {
            // Fall back to logging if Email service is unavailable
            log.warn("Email service unavailable, falling back to logging: {}", e.getMessage());
            log.info("Notification template={} orderId={} variables={}", template, orderId, variables);
        }
    }

    /**
     * DTO for Email service request
     */
    public static class EmailRequest {
        public UUID orderId;
        public String template;
        public Map<String, String> variables;

        public EmailRequest(UUID orderId, String template, Map<String, String> variables) {
            this.orderId = orderId;
            this.template = template;
            this.variables = variables;
        }
    }
}
