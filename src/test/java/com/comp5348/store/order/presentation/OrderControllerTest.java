package com.comp5348.store.order.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.comp5348.store.order.application.service.OrderOrchestrator;
import com.comp5348.store.order.application.service.OrderQueryService;
import com.comp5348.store.order.application.service.OrderQueryService.OrderSnapshot;
import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.presentation.dto.OrderResponse;
import com.comp5348.store.order.presentation.dto.OrderStatusResponse;
import com.comp5348.store.order.presentation.dto.PlaceOrderRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderOrchestrator orchestrator;

    @Mock
    private OrderQueryService queries;

    @InjectMocks
    private OrderController controller;

    @Test
    void placeOrderReturnsOrderResponseFromLatestSnapshot() {
        String customerId = "customer-123";
        PlaceOrderRequest request = new PlaceOrderRequest("item-456", 3);
        String correlationId = "corr-789";
        UUID orderId = UUID.randomUUID();
        OrderSnapshot snapshot = snapshot(orderId, Order.Status.PAID);

        when(orchestrator.placeOrder(customerId, request.itemId(), request.qty(), correlationId))
                .thenReturn(orderId);
        when(queries.getById(orderId)).thenReturn(snapshot);

        OrderResponse response = controller.placeOrder(customerId, request, correlationId);

        assertEquals(orderId, response.orderId());
        assertEquals(snapshot.status().name(), response.status());
        verify(orchestrator).placeOrder(customerId, request.itemId(), request.qty(), correlationId);
        verify(queries).getById(orderId);
    }

    @Test
    void getOrderReturnsOrderStatus() {
        UUID orderId = UUID.randomUUID();
        OrderSnapshot snapshot = snapshot(orderId, Order.Status.RESERVED);
        when(queries.getById(orderId)).thenReturn(snapshot);

        OrderStatusResponse response = controller.getOrder(orderId);

        assertEquals(orderId, response.orderId());
        assertEquals(snapshot.status().name(), response.status());
        verify(queries).getById(orderId);
    }

    @Test
    void cancelOrderDelegatesAndReturnsUpdatedStatus() {
        UUID orderId = UUID.randomUUID();
        String correlationId = "cancel-123";
        OrderSnapshot snapshot = snapshot(orderId, Order.Status.CANCELLED);
        when(queries.getById(orderId)).thenReturn(snapshot);

        OrderResponse response = controller.cancelOrder(orderId, correlationId);

        assertEquals(orderId, response.orderId());
        assertEquals(snapshot.status().name(), response.status());
        verify(orchestrator).cancel(orderId, correlationId);
        verify(queries).getById(orderId);
    }

    private OrderSnapshot snapshot(UUID orderId, Order.Status status) {
        Instant createdAt = Instant.parse("2024-05-01T10:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(60);
        return new OrderSnapshot(
                orderId,
                "customer-123",
                "item-456",
                3,
                status,
                createdAt,
                updatedAt);
    }
}

