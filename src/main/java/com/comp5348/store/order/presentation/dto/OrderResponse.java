package com.comp5348.store.order.presentation.dto;

import java.util.UUID;

public record OrderResponse(UUID orderId, String status) {
}
