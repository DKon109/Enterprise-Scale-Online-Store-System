package com.comp5348.store.order.api;

import java.util.UUID;

public record OrderResponse(UUID orderId, String status) {
}
