package com.comp5348.store.order.application.event;

import java.util.UUID;

public final class IntegrationEvents {

    private IntegrationEvents() {
    }

    public static IntegrationEvent orderPlaced(UUID orderId, String customerId, String itemId, int quantity) {
        String payload = """
                {"orderId":"%s","customerId":"%s","itemId":"%s","qty":%d}
                """.formatted(orderId, customerId, itemId, quantity);
        return new IntegrationEvent(orderId, "OrderPlaced", payload.strip());
    }

    public static IntegrationEvent orderStatusChanged(UUID orderId, String status) {
        String payload = """
                {"orderId":"%s","status":"%s"}
                """.formatted(orderId, status);
        return new IntegrationEvent(orderId, "OrderStatusChanged", payload.strip());
    }
}
