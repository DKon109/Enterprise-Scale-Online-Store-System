package com.comp5348.store.order.application.util;

import java.util.UUID;

public final class IdempotencyKeyGenerator {

    private IdempotencyKeyGenerator() {
    }

    public static String forOrder(UUID orderId) {
        return "order-" + orderId;
    }
}
