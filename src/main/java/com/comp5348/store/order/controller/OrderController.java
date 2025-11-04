package com.comp5348.store.order.controller;

import com.comp5348.store.order.application.service.OrderOrchestrator;
import com.comp5348.store.order.exception.OrderNotFoundException;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller exposing Order use cases.
 *
 * Provides a thin HTTP adapter that validates input, delegates to the service layer,
 * and maps domain models to response DTOs.
 */
@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private final OrderOrchestrator orderOrchestrator;
    private final OrderService orderService;

    public OrderController(OrderOrchestrator orderOrchestrator, OrderService orderService) {
        this.orderOrchestrator = orderOrchestrator;
        this.orderService = orderService;
    }

    /**
     * Place a new order.
     *
     * @param request input payload
     * @return created order details
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestBody @Valid PlaceOrderRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {

        String trimmedCorrelationId = StringUtils.hasText(correlationId) ? correlationId.trim() : null;
        String trimmedRequestId = StringUtils.hasText(requestId) ? requestId.trim() : null;

        UUID orderId = orderOrchestrator.placeOrder(
                request.customerId,
                request.itemId,
                request.quantity,
                trimmedCorrelationId,
                trimmedRequestId);
        Order created = orderService.getOrder(orderId);
        OrderResponse body = OrderResponse.from(created);
        return ResponseEntity.created(URI.create("/orders/" + created.getOrderId())).body(body);
    }

    /**
     * Retrieve an order by its identifier.
     */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return OrderResponse.from(orderService.getOrder(orderId));
    }

    /**
     * List orders for a given customer.
     *
     * Requirement: multiple users are supported, so the API exposes
     * a customer scoped query for UI consumption.
     */
    @GetMapping
    public List<OrderResponse> getOrdersByCustomer(@RequestParam("customerId") UUID customerId) {
        return orderService.getCustomerOrders(customerId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Cancel an order prior to shipment.
     */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable UUID orderId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        try {
            orderOrchestrator.cancel(orderId, StringUtils.hasText(correlationId) ? correlationId.trim() : null);
            Order updated = orderService.getOrder(orderId);
            return OrderResponse.from(updated);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Translate domain not-found into HTTP 404.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<String> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /**
     * Translate validation failures into HTTP 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    /**
     * Request payload for placing an order.
     */
    public record PlaceOrderRequest(
            @NotNull UUID customerId,
            @NotBlank String itemId,
            @Min(1) int quantity) {}

    /**
     * Response payload exposed to clients.
     */
    public record OrderResponse(
            UUID orderId,
            UUID customerId,
            String itemId,
            int quantity,
            String status,
            String createdAt,
            String updatedAt,
            String correlationId) {

        static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.getOrderId(),
                    order.getCustomerId(),
                    order.getItemId(),
                    order.getQuantity(),
                    order.getStatus().name(),
                    order.getCreatedAt().toString(),
                    order.getUpdatedAt().toString(),
                    order.getCorrelationId());
        }
    }
}
