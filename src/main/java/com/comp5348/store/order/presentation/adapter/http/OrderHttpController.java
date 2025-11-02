package com.comp5348.store.order.presentation.adapter.http;

import com.comp5348.store.order.presentation.OrderController;
import com.comp5348.store.order.presentation.dto.OrderResponse;
import com.comp5348.store.order.presentation.dto.OrderStatusResponse;
import com.comp5348.store.order.presentation.dto.PlaceOrderRequest;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter that bridges Spring MVC requests to the framework-agnostic order controller.
 */
@RestController
@RequestMapping("/orders")
public class OrderHttpController {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final OrderController delegate;

    public OrderHttpController(OrderController delegate) {
        this.delegate = delegate;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            Principal principal,
            @RequestBody PlaceOrderRequest request,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationId) {
        String customerId = resolveCustomerId(principal);
        OrderResponse response = delegate.placeOrder(customerId, request, normalizeCorrelationId(correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public OrderStatusResponse getOrder(@PathVariable UUID orderId) {
        return delegate.getOrder(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable UUID orderId,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationId) {
        return delegate.cancelOrder(orderId, normalizeCorrelationId(correlationId));
    }

    private String resolveCustomerId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalStateException("Authenticated principal required to place orders");
        }
        return principal.getName();
    }

    private String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }
        return correlationId.trim();
    }
}
