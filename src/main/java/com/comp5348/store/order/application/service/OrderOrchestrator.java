package com.comp5348.store.order.application.service;

import com.comp5348.store.order.application.event.IntegrationEvents;
import com.comp5348.store.order.application.event.OutboxPublisher;
import com.comp5348.store.order.application.policy.CircuitBreaker;
import com.comp5348.store.order.application.policy.CircuitBreakerOpenException;
import com.comp5348.store.order.application.policy.RetryPolicy;
import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import com.comp5348.store.order.application.util.IdempotencyKeyGenerator;
import com.comp5348.store.order.domain.model.Money;
import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.domain.model.OrderSagaState;
import com.comp5348.store.order.domain.model.OrderTimelineEntry;
import com.comp5348.store.order.domain.repository.OrderEventRepository;
import com.comp5348.store.order.domain.repository.OrderRepository;
import com.comp5348.store.order.domain.repository.OrderSagaStateRepository;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class OrderOrchestrator {

    private static final Duration[] DEFAULT_BACKOFF = new Duration[]{
            Duration.ofMillis(200),
            Duration.ofMillis(500),
            Duration.ofMillis(1000)
    };

    private final OrderRepository orders;
    private final InventoryServicePort inventory;
    private final PaymentServicePort payments;
    private final ShippingServicePort shipping;
    private final NotificationServicePort notifications;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker circuitBreaker;
    private final OutboxPublisher outboxPublisher;
    private final OrderSagaStateRepository sagaStates;
    private final OrderEventRepository events;
    private final InterServiceCallLogger callLogger;

    public OrderOrchestrator(
            OrderRepository orders,
            InventoryServicePort inventory,
            PaymentServicePort payments,
            ShippingServicePort shipping,
            NotificationServicePort notifications,
            RetryPolicy retryPolicy,
            CircuitBreaker circuitBreaker,
            OutboxPublisher outboxPublisher,
            OrderSagaStateRepository sagaStates,
            OrderEventRepository events,
            InterServiceCallLogger callLogger) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.shipping = Objects.requireNonNull(shipping, "shipping");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.retryPolicy = retryPolicy == null ? RetryPolicy.exponential(DEFAULT_BACKOFF) : retryPolicy;
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.outboxPublisher = Objects.requireNonNull(outboxPublisher, "outboxPublisher");
        this.sagaStates = Objects.requireNonNull(sagaStates, "sagaStates");
        this.events = Objects.requireNonNull(events, "events");
        this.callLogger = callLogger == null ? InterServiceCallLogger.noop() : callLogger;
    }

    public UUID placeOrder(String customerId, String itemId, int quantity) {
        return placeOrder(customerId, itemId, quantity, null);
    }

    public UUID placeOrder(String customerId, String itemId, int quantity, String correlationId) {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, itemId, quantity);

        orders.save(order);
        outboxPublisher.append(IntegrationEvents.orderPlaced(orderId, customerId, itemId, quantity));
        persistSaga(orderId, "RESERVING_STOCK");
        recordEvent(orderId, "OrderPlaced", Map.of("qty", quantity, "itemId", itemId));

        var allocations = executeWithRetryAndLogging(
                "ReserveStock",
                orderId,
                correlationId,
                () -> {
                    InventoryServicePort.ReserveResult result = inventory.reserve(orderId, itemId, quantity);
                    if (!result.isSuccess()) {
                        throw new IllegalStateException("Stock reservation failed: " + result.reason());
                    }
                    return result.allocations();
                },
                "Stock reservation failed after retries");

        order.markReserved();
        orders.save(order);
        outboxPublisher.append(IntegrationEvents.orderStatusChanged(orderId, order.getStatus().name()));
        recordEvent(orderId, "StockReserved", Map.of("allocations", allocations.size()));
        persistSaga(orderId, "AUTHORIZING_PAYMENT");

        Money totalAmount = Money.of(quantity);
        String idempotencyKey = IdempotencyKeyGenerator.forOrder(orderId);

        PaymentServicePort.PaymentResult paymentResult = invokeWithCircuitBreaker(() ->
                executeWithRetryAndLogging(
                        "AuthorizePayment",
                        orderId,
                        correlationId,
                        () -> payments.authorize(orderId, totalAmount, idempotencyKey),
                        "Payment authorization exhausted"));

        if (!paymentResult.isAuthorized()) {
            handlePaymentFailure(order, paymentResult.reason(), correlationId);
            return orderId;
        }

        order.markPaid();
        orders.save(order);
        outboxPublisher.append(IntegrationEvents.orderStatusChanged(orderId, order.getStatus().name()));
        recordEvent(orderId, "PaymentAuthorized", Map.of("amount", totalAmount.toString()));
        persistSaga(orderId, "REQUESTING_SHIPMENT");

        ShippingServicePort.ShipmentResult shipmentResult = executeWithRetryAndLogging(
                "RequestShipment",
                orderId,
                correlationId,
                () -> shipping.request(orderId, allocations),
                "Shipment request exhausted");

        if (!shipmentResult.isAccepted()) {
            handleShipmentFailure(order, "Shipment rejected", correlationId);
            return orderId;
        }

        order.markShipmentRequested();
        orders.save(order);
        outboxPublisher.append(IntegrationEvents.orderStatusChanged(orderId, order.getStatus().name()));
        recordEvent(orderId, "ShipmentRequested", shipmentResult.trackingId() == null
                ? Map.of()
                : Map.of("trackingId", shipmentResult.trackingId()));
        persistSaga(orderId, "WAITING_FOR_DELIVERY");

        return orderId;
    }

    public void markDelivered(UUID orderId) {
        Order order = orders.getRequired(orderId);
        order.markDelivered();
        orders.save(order);
        outboxPublisher.append(IntegrationEvents.orderStatusChanged(orderId, order.getStatus().name()));
        recordEvent(orderId, "Delivered", Map.of());
        sagaStates.delete(orderId);
    }

    public void cancel(UUID orderId) {
        cancel(orderId, null);
    }

    public void cancel(UUID orderId, String correlationId) {
        Order order = orders.getRequired(orderId);
        if (!order.canCancel()) {
            throw new IllegalStateException("Order can no longer be cancelled");
        }

        if (order.getStatus() == Order.Status.PAID) {
            invokeAndLog("RefundPayment", orderId, correlationId, () -> payments.refund(orderId));
        }
        invokeAndLog("ReleaseStock", orderId, correlationId, () -> inventory.release(orderId));

        order.cancel();
        orders.save(order);
        outboxPublisher.append(IntegrationEvents.orderStatusChanged(order.getOrderId(), order.getStatus().name()));
        recordEvent(order.getOrderId(), "OrderCancelled", Map.of());
        sagaStates.delete(order.getOrderId());
    }

    private void handlePaymentFailure(Order order, String reason, String correlationId) {
        invokeAndLog("ReleaseStock", order.getOrderId(), correlationId, () -> inventory.release(order.getOrderId()));
        order.cancel();
        orders.save(order);
        outboxPublisher.append(IntegrationEvents.orderStatusChanged(order.getOrderId(), order.getStatus().name()));
        recordEvent(order.getOrderId(), "PaymentFailed", Map.of("reason", reason));
        notifications.send(order.getOrderId(), "PAYMENT_FAILED", Map.of("reason", reason));
        sagaStates.delete(order.getOrderId());
    }

    private void handleShipmentFailure(Order order, String reason, String correlationId) {
        invokeAndLog("RefundPayment", order.getOrderId(), correlationId, () -> payments.refund(order.getOrderId()));
        invokeAndLog("ReleaseStock", order.getOrderId(), correlationId, () -> inventory.release(order.getOrderId()));
        order.cancel();
        orders.save(order);
        outboxPublisher.append(IntegrationEvents.orderStatusChanged(order.getOrderId(), order.getStatus().name()));
        recordEvent(order.getOrderId(), "ShipmentFailed", Map.of("reason", reason));
        notifications.send(order.getOrderId(), "SHIPMENT_FAILED", Map.of("reason", reason));
        sagaStates.delete(order.getOrderId());
    }

    private void persistSaga(UUID orderId, String step) {
        sagaStates.save(new OrderSagaState(orderId, step, 0, null, Instant.now()));
    }

    private void recordEvent(UUID orderId, String event, Map<String, ?> payload) {
        Map<String, Object> safePayload = new java.util.HashMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> {
                if (value != null) {
                    safePayload.put(key, value);
                }
            });
        }
        events.record(new OrderTimelineEntry(orderId, event, safePayload, Instant.now()));
    }

    private <T> T executeWithRetryAndLogging(
            String step,
            UUID orderId,
            String correlationId,
            RetryPolicy.CheckedSupplier<T> supplier,
            String failureMessage) {
        final long[] latency = new long[1];
        return retryPolicy.execute(() -> timedCall(supplier, latency), new RetryPolicy.AttemptListener() {
            @Override
            public void onSuccess(int attemptNumber) {
                callLogger.log(orderId, correlationId, step, attemptNumber, latency[0], "SUCCESS");
            }

            @Override
            public void onFailure(int attemptNumber, Exception error, boolean willRetry) {
                callLogger.log(orderId, correlationId, step, attemptNumber, latency[0], willRetry ? "RETRYING" : "FAILED");
            }
        }).orElseThrow(() -> new IllegalStateException(failureMessage));
    }

    private <T> T timedCall(RetryPolicy.CheckedSupplier<T> supplier, long[] latency) throws Exception {
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            latency[0] = toMillis(start);
            return result;
        } catch (Exception ex) {
            latency[0] = toMillis(start);
            throw ex;
        }
    }

    private void invokeAndLog(String step, UUID orderId, String correlationId, Runnable action) {
        long start = System.nanoTime();
        action.run();
        callLogger.log(orderId, correlationId, step, 1, toMillis(start), "SUCCESS");
    }

    private <T> T invokeWithCircuitBreaker(RetryPolicy.CheckedSupplier<T> supplier) {
        try {
            return circuitBreaker.protect(supplier::get);
        } catch (CircuitBreakerOpenException ex) {
            throw new IllegalStateException("Circuit breaker open for payment service", ex);
        }
    }

    private long toMillis(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }
}
