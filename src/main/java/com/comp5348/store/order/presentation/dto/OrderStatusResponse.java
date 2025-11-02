package com.comp5348.store.order.presentation.dto;

import java.util.UUID;

public record OrderStatusResponse(
        UUID orderId,
        String status) {
}
