package com.comp5348.store.order.infrastructure.adapter.notification;

import com.comp5348.store.order.application.port.NotificationServicePort;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Notification adapter that simply logs outgoing notifications. This is sufficient for
 * local development while keeping the application layer blindness to delivery mechanics.
 */
@Component
public class LoggingNotificationServiceAdapter implements NotificationServicePort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationServiceAdapter.class);

    @Override
    public void send(UUID orderId, String template, Map<String, String> variables) {
        log.info("Notification template={} orderId={} variables={}", template, orderId, variables);
    }
}
