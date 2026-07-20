package com.comp5348.store.order.application.service;

import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.messaging.events.EventMessage;
import com.comp5348.store.order.application.policy.CircuitBreaker;
import com.comp5348.store.order.application.policy.CircuitBreakerOpenException;
import com.comp5348.store.order.application.policy.RetryPolicy;
import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import com.comp5348.store.order.application.support.TransactionTemplate;
import com.comp5348.store.order.application.util.IdempotencyKeyGenerator;
import com.comp5348.store.order.exception.OrderNotFoundException;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import com.comp5348.store.order.model.Money;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.repository.OrderRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class OrderOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderOrchestrator.class);

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
    private final RabbitTemplate rabbitTemplate;
    @Value("${app.messaging.enabled:true}")
    private boolean messagingEnabled = true;

    public OrderOrchestrator(
            OrderRepository orders,
            InventoryServicePort inventory,
            PaymentServicePort payments,
            ShippingServicePort shipping,
            NotificationServicePort notifications,
            TransactionTemplate transactions,
            RetryPolicy retryPolicy,
            CircuitBreaker circuitBreaker,
            InterServiceCallLogger callLogger,
            RabbitTemplate rabbitTemplate) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.shipping = Objects.requireNonNull(shipping, "shipping");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.retryPolicy = retryPolicy == null ? RetryPolicy.exponential(DEFAULT_BACKOFF) : retryPolicy;
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.callLogger = callLogger == null ? InterServiceCallLogger.noop() : callLogger;
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate");
    }

    public UUID placeOrder(UUID customerId, String itemId, int quantity) {
        return placeOrder(customerId, itemId, quantity, null, null);
    }

    public UUID placeOrder(UUID customerId, String itemId, int quantity, String correlationId) {
        return placeOrder(customerId, itemId, quantity, correlationId, null);
    }

    public UUID placeOrder(UUID customerId, String itemId, int quantity, String correlationId, String requestId) {
        String normalisedCorrelationId = normalise(correlationId);
        String normalisedRequestId = normalise(requestId);

        if (normalisedRequestId != null) {
            return orders.findByRequestId(normalisedRequestId)
                    .map(Order::getOrderId)
                    .orElseGet(() -> createAndProcessOrder(customerId, itemId, quantity, normalisedCorrelationId, normalisedRequestId));
        }

        return createAndProcessOrder(customerId, itemId, quantity, normalisedCorrelationId, null);
    }

    private UUID createAndProcessOrder(UUID customerId, String itemId, int quantity, String correlationId, String requestId) {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, itemId, quantity);
        order.setCorrelationId(correlationId);
        order.setRequestId(requestId);

        transactions.execute(() -> orders.save(order));

        return orderId;
    }

    /**
     * Step 1: Reserve stock for a PENDING order.
     * PENDING → RESERVED
     */
    public void reserveStock(UUID orderId, String correlationId) {
        Order order = getOrderRequired(orderId);

        if (order.getStatus() != Order.Status.PENDING) {
            throw new IllegalStateException("Order must be PENDING to reserve stock, but is " + order.getStatus());
        }

        try {
            List<InventoryServicePort.Allocation> allocations = executeWithRetryAndLogging(
                    "ReserveStock",
                    orderId,
                    correlationId,
                    () -> {
                        InventoryServicePort.ReserveResult result = inventory.reserve(orderId, order.getItemId(), order.getQuantity());
                        if (!result.isSuccess()) {
                            throw new IllegalStateException("Stock reservation failed: " + result.reason());
                        }
                        return result.allocations();
                    },
                    "Stock reservation failed after retries");

            order.markReserved();
            transactions.execute(() -> orders.save(order));
        } catch (IllegalStateException reserveFailure) {
            String detailedReason = extractFailureReason(reserveFailure);
            String displayReason = normaliseReservationReason(detailedReason);
            handleReservationFailure(order, displayReason, detailedReason);
            throw reserveFailure;
        }
    }

    /**
     * Step 2: Authorize payment for a RESERVED order.
     * RESERVED → PAID
     */
    public void authorizePayment(UUID orderId, String correlationId) {
        Order order = getOrderRequired(orderId);

        if (order.getStatus() != Order.Status.RESERVED) {
            throw new IllegalStateException("Order must be RESERVED to authorize payment, but is " + order.getStatus());
        }

        Money totalAmount = Money.of(order.getQuantity());
        String idempotencyKey = IdempotencyKeyGenerator.forOrder(orderId);

        PaymentServicePort.PaymentResult paymentResult = invokeWithCircuitBreaker(() ->
                executeWithRetryAndLogging(
                        "AuthorizePayment",
                        orderId,
                        correlationId,
                        () -> payments.authorize(orderId, totalAmount, idempotencyKey, correlationId, order.getRequestId()),
                        "Payment authorization exhausted"));

        if (!paymentResult.isAuthorized()) {
            handlePaymentFailure(order, paymentResult.reason(), correlationId);
            throw new IllegalStateException("Payment authorization failed: " + paymentResult.reason());
        }

        order.markPaid();
        transactions.execute(() -> orders.save(order));

        // Publish payment authorized event to message queue
        publishPaymentAuthorizedEvent(orderId, correlationId);
    }

    /**
     * Process shipment request for a PAID order.
     * This is called asynchronously by ShipmentWorker to allow cancellation window.
     *
     * COMPLIANCE: §50-54 - Creates window for pre-shipment cancellation
     */
    public void processShipment(UUID orderId, String correlationId) {
        Order order = getOrderRequired(orderId);

        // Only process if order is still in PAID status (not cancelled)
        if (order.getStatus() != Order.Status.PAID) {
            return;
        }

        // Get allocations from inventory
        List<InventoryServicePort.Allocation> allocations = inventory.allocations(orderId);
        if (allocations == null || allocations.isEmpty()) {
            handleShipmentFailure(order, "Missing inventory allocations", correlationId);
            return;
        }

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
            return;
        }

        if (!shipmentResult.isAccepted()) {
            handleShipmentFailure(order, "Shipment rejected", correlationId);
            return;
        }

        try {
            invokeAndLog("CommitReservedStock", orderId, correlationId, () -> inventory.deduct(orderId));
        } catch (RuntimeException commitFailure) {
            handleShipmentFailure(order, extractFailureReason(commitFailure), correlationId);
            return;
        }

        order.markShipmentRequested();
        transactions.execute(() -> orders.save(order));

        // Publish shipment requested event to message queue
        publishShipmentRequestedEvent(orderId, correlationId);
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
            PaymentServicePort.PaymentResult refundResult = invokeRefund(orderId, correlationId, order.getRequestId());
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
        PaymentServicePort.PaymentResult refundResult = invokeRefund(order.getOrderId(), correlationId, order.getRequestId());
        invokeAndLog("ReleaseStock", order.getOrderId(), correlationId, () -> inventory.release(order.getOrderId()));

        transactions.execute(() -> {
            order.markShipmentFailed();
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

    private PaymentServicePort.PaymentResult invokeRefund(UUID orderId, String correlationId, String requestId) {
        long start = System.nanoTime();
        try {
            PaymentServicePort.PaymentResult result = payments.refund(orderId, correlationId, requestId);
            String outcome = result.isAuthorized() ? "SUCCESS" : "FAILED";
            callLogger.log(orderId, correlationId, "RefundPayment", 1, toMillis(start), outcome);
            return result;
        } catch (RuntimeException ex) {
            callLogger.log(orderId, correlationId, "RefundPayment", 1, toMillis(start), "FAILED");
            throw ex;
        }
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
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private String normalise(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    // ===== Event Publishing to RabbitMQ =====

    /**
     * Publish payment authorized event to bank_queue.
     * Listeners: BankMessageListener, EmailMessageListener
     */
    private void publishPaymentAuthorizedEvent(UUID orderId, String correlationId) {
        if (!messagingEnabled) {
            log.info("[OrderOrchestrator] Demo mode: recorded payment.authorized for order {}", orderId);
            return;
        }
        try {
            EventMessage event = new EventMessage();
            event.setType("payment.authorized");
            event.setOrderId(orderId);
            event.setDescription("Payment authorized for order " + orderId);
            event.setCorrelationId(correlationId);
            event.setTimestamp(LocalDateTime.now());
            event.setRetryCount(0);

            rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, event);
            log.info("[OrderOrchestrator] Published payment.authorized event for order {} to {}", orderId, RabbitMQConfig.BANK_QUEUE);
        } catch (Exception e) {
            log.error("[OrderOrchestrator] Failed to publish payment.authorized event for order {}: {}", orderId, e.getMessage(), e);
            // Don't throw - event publishing failure shouldn't block order processing
        }
    }

    /**
     * Publish shipment requested event to warehouse_queue.
     * Listeners: WarehouseMessageListener
     */
    private void publishShipmentRequestedEvent(UUID orderId, String correlationId) {
        if (!messagingEnabled) {
            log.info("[OrderOrchestrator] Demo mode: recorded shipment.requested for order {}", orderId);
            return;
        }
        try {
            EventMessage event = new EventMessage();
            event.setType("shipment.requested");
            event.setOrderId(orderId);
            event.setDescription("Shipment requested for order " + orderId);
            event.setCorrelationId(correlationId);
            event.setTimestamp(LocalDateTime.now());
            event.setRetryCount(0);

            rabbitTemplate.convertAndSend(RabbitMQConfig.WAREHOUSE_QUEUE, event);
            log.info("[OrderOrchestrator] Published shipment.requested event for order {} to {}", orderId, RabbitMQConfig.WAREHOUSE_QUEUE);
        } catch (Exception e) {
            log.error("[OrderOrchestrator] Failed to publish shipment.requested event for order {}: {}", orderId, e.getMessage(), e);
            // Don't throw - event publishing failure shouldn't block order processing
        }
    }
}
