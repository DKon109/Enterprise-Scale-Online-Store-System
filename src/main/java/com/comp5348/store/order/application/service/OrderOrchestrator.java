package com.comp5348.store.order.application.service;

import com.comp5348.store.order.application.policy.CircuitBreaker;
import com.comp5348.store.order.application.policy.CircuitBreakerOpenException;
import com.comp5348.store.order.application.policy.RetryPolicy;
import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import com.comp5348.store.order.application.support.TransactionTemplate;
import com.comp5348.store.order.application.util.IdempotencyKeyGenerator;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import com.comp5348.store.order.model.Money;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.repository.OrderRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
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
    private final TransactionTemplate transactions;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker circuitBreaker;
    private final InterServiceCallLogger callLogger;

    public OrderOrchestrator(
            OrderRepository orders,
            InventoryServicePort inventory,
            PaymentServicePort payments,
            ShippingServicePort shipping,
            NotificationServicePort notifications,
            TransactionTemplate transactions,
            RetryPolicy retryPolicy,
            CircuitBreaker circuitBreaker,
            InterServiceCallLogger callLogger) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.shipping = Objects.requireNonNull(shipping, "shipping");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.retryPolicy = retryPolicy == null ? RetryPolicy.exponential(DEFAULT_BACKOFF) : retryPolicy;
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.callLogger = callLogger == null ? InterServiceCallLogger.noop() : callLogger;
    }

    public UUID placeOrder(UUID customerId, String itemId, int quantity) {
        return placeOrder(customerId, itemId, quantity, null);
    }

    public UUID placeOrder(UUID customerId, String itemId, int quantity, String correlationId) {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, itemId, quantity);

        transactions.execute(() -> {
            orders.save(order);
        });

        List<InventoryServicePort.Allocation> allocations;
        try {
            allocations = executeWithRetryAndLogging(
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
        } catch (IllegalStateException reserveFailure) {
            String detailedReason = extractFailureReason(reserveFailure);
            String displayReason = normaliseReservationReason(detailedReason);
            handleReservationFailure(order, displayReason, detailedReason);
            return orderId;
        }

        order.markReserved();
        transactions.execute(() -> {
            orders.save(order);
        });

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
        transactions.execute(() -> {
            orders.save(order);
        });

        ShippingServicePort.ShipmentResult shipmentResult;
        try {
            shipmentResult = executeWithRetryAndLogging(
                    "RequestShipment",
                    orderId,
                    correlationId,
                    () -> shipping.request(orderId, allocations),
                    "Shipment request exhausted");
        } catch (IllegalStateException shippingFailure) {
            handleShipmentFailure(order, extractFailureReason(shippingFailure), correlationId);
            return orderId;
        }

        if (!shipmentResult.isAccepted()) {
            handleShipmentFailure(order, "Shipment rejected", correlationId);
            return orderId;
        }

        order.markShipmentRequested();
        transactions.execute(() -> {
            orders.save(order);
        });

        return orderId;
    }

    public void markDelivered(UUID orderId) {
        Order order = getOrderRequired(orderId);
        transactions.execute(() -> {
            order.markDelivered();
            orders.save(order);
        });
    }

    public void cancel(UUID orderId) {
        cancel(orderId, null);
    }

    public void cancel(UUID orderId, String correlationId) {
        Order order = getOrderRequired(orderId);
        if (!order.canCancel()) {
            throw new IllegalStateException("Order can no longer be cancelled");
        }

        if (order.getStatus() == Order.Status.PAID) {
            invokeAndLog("RefundPayment", orderId, correlationId, () -> payments.refund(orderId));
        }
        invokeAndLog("ReleaseStock", orderId, correlationId, () -> inventory.release(orderId));

        transactions.execute(() -> {
            order.cancel();
            orders.save(order);
        });
    }

    private void handleReservationFailure(Order order, String displayReason, String detailedReason) {
        transactions.execute(() -> {
            order.cancel();
            orders.save(order);
        });

        Map<String, String> notificationPayload = new HashMap<>();
        notificationPayload.put("itemId", order.getItemId());
        if (displayReason != null && !displayReason.isBlank()) {
            notificationPayload.put("reason", displayReason);
        }
        String template = (displayReason == null || displayReason.isBlank())
                ? "STOCK_RESERVATION_FAILED"
                : displayReason;
        notifications.send(order.getOrderId(), template, notificationPayload);
    }

    private void handlePaymentFailure(Order order, String reason, String correlationId) {
        invokeAndLog("ReleaseStock", order.getOrderId(), correlationId, () -> inventory.release(order.getOrderId()));

        transactions.execute(() -> {
            order.cancel();
            orders.save(order);
        });

        notifications.send(order.getOrderId(), "PAYMENT_FAILED", notificationPayload(reason));
    }

    private void handleShipmentFailure(Order order, String reason, String correlationId) {
        invokeAndLog("RefundPayment", order.getOrderId(), correlationId, () -> payments.refund(order.getOrderId()));
        invokeAndLog("ReleaseStock", order.getOrderId(), correlationId, () -> inventory.release(order.getOrderId()));

        transactions.execute(() -> {
            order.cancel();
            orders.save(order);
        });

        notifications.send(order.getOrderId(), "SHIPMENT_FAILED", notificationPayload(reason));
    }

    private Map<String, String> notificationPayload(String reason) {
        if (reason == null || reason.isBlank()) {
            return Map.of();
        }
        return Map.of("reason", reason);
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

    private String extractFailureReason(Throwable error) {
        if (error == null) {
            return null;
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private String normaliseReservationReason(String rawReason) {
        if (rawReason == null) {
            return null;
        }
        String trimmed = rawReason.trim();
        String prefix = "Stock reservation failed:";
        if (trimmed.startsWith(prefix)) {
            return trimmed.substring(prefix.length()).trim();
        }
        int colonIndex = trimmed.lastIndexOf(':');
        if (colonIndex != -1 && colonIndex < trimmed.length() - 1) {
            return trimmed.substring(colonIndex + 1).trim();
        }
        return trimmed;
    }

    private Order getOrderRequired(UUID orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order %s not found".formatted(orderId)));
    }
}
