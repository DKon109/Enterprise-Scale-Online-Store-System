# OrderOrchestrator Event Publishing - Implementation Guide

## Overview

The OrderOrchestrator needs to be updated to publish events to RabbitMQ after each successful operation.

**Current State**: No events published
**Target State**: Events published for payment, shipment, and failures

---

## Changes Required

### 1. Add RabbitTemplate Dependency

```java
@Service
public class OrderOrchestrator {
    
    private final RabbitTemplate rabbitTemplate;  // ← ADD THIS
    private final OrderRepository orders;
    private final PaymentServicePort payments;
    private final ShippingServicePort shipping;
    private final InventoryServicePort inventory;
    
    public OrderOrchestrator(
        RabbitTemplate rabbitTemplate,           // ← ADD THIS
        OrderRepository orders,
        PaymentServicePort payments,
        ShippingServicePort shipping,
        InventoryServicePort inventory,
        RetryPolicy retryPolicy,
        CircuitBreaker circuitBreaker
    ) {
        this.rabbitTemplate = rabbitTemplate;    // ← ADD THIS
        this.orders = orders;
        this.payments = payments;
        this.shipping = shipping;
        this.inventory = inventory;
        this.retryPolicy = retryPolicy;
        this.circuitBreaker = circuitBreaker;
    }
}
```

### 2. Add Event Publishing Methods

Add these methods to OrderOrchestrator:

```java
/**
 * Publish payment success event to bank_queue.
 * Called after payment authorization succeeds.
 */
private void publishPaymentSuccessEvent(UUID orderId, Money amount, String correlationId, String idempotencyKey) {
    EventMessage event = new EventMessage(
        "payment.success",
        orderId,
        "Payment authorized for order " + orderId
    );
    event.setAmount(amount.amount().doubleValue());
    event.setCorrelationId(correlationId);
    event.setIdempotencyKey(idempotencyKey);
    
    try {
        rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, event);
        log.info("[OrderOrchestrator] ✅ Published payment.success event for order {} | Correlation: {}", 
            orderId, correlationId);
    } catch (Exception e) {
        log.error("[OrderOrchestrator] ❌ Failed to publish payment.success event for order {}: {}", 
            orderId, e.getMessage());
        // Note: Order is already paid, so we don't fail the order
        // The event will be retried by RabbitMQ
    }
}

/**
 * Publish payment failed event to bank_queue.
 * Called when payment authorization fails.
 */
private void publishPaymentFailedEvent(UUID orderId, String reason, String correlationId) {
    EventMessage event = new EventMessage(
        "payment.failed",
        orderId,
        "Payment failed: " + reason
    );
    event.setCorrelationId(correlationId);
    
    try {
        rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, event);
        log.info("[OrderOrchestrator] ✅ Published payment.failed event for order {}", orderId);
    } catch (Exception e) {
        log.error("[OrderOrchestrator] ❌ Failed to publish payment.failed event for order {}: {}", 
            orderId, e.getMessage());
    }
}

/**
 * Publish shipment requested event to warehouse_queue.
 * Called after shipment request succeeds.
 */
private void publishShipmentRequestedEvent(UUID orderId, String correlationId, String idempotencyKey) {
    EventMessage event = new EventMessage(
        "shipment.requested",
        orderId,
        "Shipment requested for order " + orderId
    );
    event.setCorrelationId(correlationId);
    event.setIdempotencyKey(idempotencyKey);
    
    try {
        rabbitTemplate.convertAndSend(RabbitMQConfig.WAREHOUSE_QUEUE, event);
        log.info("[OrderOrchestrator] ✅ Published shipment.requested event for order {} | Correlation: {}", 
            orderId, correlationId);
    } catch (Exception e) {
        log.error("[OrderOrchestrator] ❌ Failed to publish shipment.requested event for order {}: {}", 
            orderId, e.getMessage());
    }
}

/**
 * Publish order placed event to email_queue.
 * Called after order is successfully placed.
 */
private void publishOrderPlacedEvent(UUID orderId, String customerEmail, String correlationId) {
    EventMessage event = new EventMessage(
        "order.placed",
        orderId,
        "Order placed successfully"
    );
    event.setCustomerEmail(customerEmail);
    event.setCorrelationId(correlationId);
    
    try {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EMAIL_QUEUE, event);
        log.info("[OrderOrchestrator] ✅ Published order.placed event for order {} | Correlation: {}", 
            orderId, correlationId);
    } catch (Exception e) {
        log.error("[OrderOrchestrator] ❌ Failed to publish order.placed event for order {}: {}", 
            orderId, e.getMessage());
    }
}

/**
 * Publish order cancelled event to email_queue.
 * Called when order is cancelled.
 */
private void publishOrderCancelledEvent(UUID orderId, String customerEmail, String reason, String correlationId) {
    EventMessage event = new EventMessage(
        "order.cancelled",
        orderId,
        "Order cancelled: " + reason
    );
    event.setCustomerEmail(customerEmail);
    event.setCorrelationId(correlationId);
    
    try {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EMAIL_QUEUE, event);
        log.info("[OrderOrchestrator] ✅ Published order.cancelled event for order {}", orderId);
    } catch (Exception e) {
        log.error("[OrderOrchestrator] ❌ Failed to publish order.cancelled event for order {}: {}", 
            orderId, e.getMessage());
    }
}
```

### 3. Update placeOrder() Method

In the `placeOrder()` method, add event publishing calls:

```java
public UUID placeOrder(UUID customerId, String itemId, int quantity, String correlationId) {
    UUID orderId = UUID.randomUUID();
    Order order = new Order(orderId, customerId, itemId, quantity);
    
    // ... existing code for reserve stock ...
    
    // After payment authorized
    order.markPaid();
    transactions.execute(() -> {
        orders.save(order);
    });
    
    // ✅ PUBLISH PAYMENT SUCCESS EVENT
    publishPaymentSuccessEvent(orderId, totalAmount, correlationId, idempotencyKey);
    
    // ... existing code for request shipment ...
    
    order.markShipmentRequested();
    transactions.execute(() -> {
        orders.save(order);
    });
    
    // ✅ PUBLISH SHIPMENT REQUESTED EVENT
    publishShipmentRequestedEvent(orderId, correlationId, idempotencyKey);
    
    // ✅ PUBLISH ORDER PLACED EVENT (for email notification)
    publishOrderPlacedEvent(orderId, order.getCustomerEmail(), correlationId);
    
    return orderId;
}
```

### 4. Update Failure Handlers

Update failure handlers to publish failure events:

```java
private void handlePaymentFailure(Order order, String reason, String correlationId) {
    order.markFailed();
    transactions.execute(() -> {
        orders.save(order);
    });
    
    // ✅ PUBLISH PAYMENT FAILED EVENT
    publishPaymentFailedEvent(order.getOrderId(), reason, correlationId);
    
    // ✅ PUBLISH ORDER CANCELLED EVENT
    publishOrderCancelledEvent(
        order.getOrderId(), 
        order.getCustomerEmail(), 
        "Payment failed: " + reason, 
        correlationId
    );
    
    log.warn("[OrderOrchestrator] Order {} payment failed: {}", order.getOrderId(), reason);
}

private void handleShipmentFailure(Order order, String reason, String correlationId) {
    order.markFailed();
    transactions.execute(() -> {
        orders.save(order);
    });
    
    // ✅ PUBLISH ORDER CANCELLED EVENT
    publishOrderCancelledEvent(
        order.getOrderId(), 
        order.getCustomerEmail(), 
        "Shipment failed: " + reason, 
        correlationId
    );
    
    log.warn("[OrderOrchestrator] Order {} shipment failed: {}", order.getOrderId(), reason);
}
```

---

## Testing the Changes

### Unit Test Example

```java
@Test
void testPaymentSuccessEventPublished() {
    // Arrange
    UUID orderId = UUID.randomUUID();
    Money amount = Money.of(1);
    String correlationId = "test-correlation-123";
    String idempotencyKey = "test-idempotency-456";
    
    // Act
    orchestrator.publishPaymentSuccessEvent(orderId, amount, correlationId, idempotencyKey);
    
    // Assert
    ArgumentCaptor<EventMessage> captor = ArgumentCaptor.forClass(EventMessage.class);
    verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.BANK_QUEUE), captor.capture());
    
    EventMessage event = captor.getValue();
    assertEquals("payment.success", event.getType());
    assertEquals(orderId, event.getOrderId());
    assertEquals(correlationId, event.getCorrelationId());
    assertEquals(idempotencyKey, event.getIdempotencyKey());
}
```

### Integration Test Example

```java
@Test
void testCompleteOrderFlowPublishesAllEvents() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    String itemId = "ITEM-001";
    int quantity = 1;
    String correlationId = "test-correlation-789";
    
    // Mock external services
    when(inventoryService.reserve(...)).thenReturn(reserveResult);
    when(paymentService.authorize(...)).thenReturn(paymentResult);
    when(shippingService.request(...)).thenReturn(shipmentResult);
    
    // Act
    UUID orderId = orchestrator.placeOrder(customerId, itemId, quantity, correlationId);
    
    // Assert
    verify(rabbitTemplate, times(3)).convertAndSend(anyString(), any(EventMessage.class));
    // Verify payment.success event
    // Verify shipment.requested event
    // Verify order.placed event
}
```

---

## Compliance Mapping

| Requirement | Implementation |
|-------------|-----------------|
| §79-80: Reliable messaging | Events published to RabbitMQ |
| §242: Idempotency | idempotencyKey included in events |
| §246: Correlation tracking | correlationId propagated through events |
| §71-83: Fault tolerance | Events published even on failure |

