package com.comp5348.store.order.presentation;

import com.comp5348.store.order.application.service.OrderOrchestrator;
import com.comp5348.store.order.application.service.OrderQueryService;
import com.comp5348.store.order.presentation.dto.OrderResponse;
import com.comp5348.store.order.presentation.dto.OrderStatusResponse;
import com.comp5348.store.order.presentation.dto.PlaceOrderRequest;
import java.util.UUID;

public class OrderController {

    private final OrderOrchestrator orchestrator;
    private final OrderQueryService queries;

    public OrderController(OrderOrchestrator orchestrator, OrderQueryService queries) {
        this.orchestrator = orchestrator;
        this.queries = queries;
    }

    public OrderResponse placeOrder(String customerId, PlaceOrderRequest request, String correlationId) {
        UUID orderId = orchestrator.placeOrder(customerId, request.itemId(), request.qty(), correlationId);
        var snapshot = queries.getById(orderId);
        return new OrderResponse(orderId, snapshot.status().name());
    }

    public OrderStatusResponse getOrder(UUID orderId) {
        var snapshot = queries.getById(orderId);
        return new OrderStatusResponse(snapshot.orderId(), snapshot.status().name());
    }

    public OrderResponse cancelOrder(UUID orderId, String correlationId) {
        orchestrator.cancel(orderId, correlationId);
        var snapshot = queries.getById(orderId);
        return new OrderResponse(orderId, snapshot.status().name());
    }
}
