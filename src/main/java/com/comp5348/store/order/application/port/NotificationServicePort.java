package com.comp5348.store.order.application.port;

import java.util.Map;
import java.util.UUID;

public interface NotificationServicePort {

    void send(UUID orderId, String template, Map<String, String> variables);
}
