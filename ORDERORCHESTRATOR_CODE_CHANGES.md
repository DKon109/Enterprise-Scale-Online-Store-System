# OrderOrchestrator Code Changes - Copy & Paste Ready

## Step 1: Add RabbitTemplate to Constructor

**Location**: `src/main/java/com/comp5348/store/order/application/service/OrderOrchestrator.java`

**Find this**:
```java
public OrderOrchestrator(
    OrderRepository orders,
    PaymentServicePort payments,
    ShippingServicePort shipping,
    InventoryServicePort inventory,
    RetryPolicy retryPolicy,
    CircuitBreaker circuitBreaker
) {
```

**Replace with**:
```java
private final RabbitTemplate rabbitTemplate;  // ← ADD THIS FIELD

public OrderOrchestrator(
    RabbitTemplate rabbitTemplate,            // ← ADD THIS PARAMETER
    OrderRepository orders,
    PaymentServicePort payments,
    ShippingServicePort shipping,
    InventoryServicePort inventory,
    RetryPolicy retryPolicy,
    CircuitBreaker circuitBreaker
) {
    this.rabbitTemplate = rabbitTemplate;     // ← ADD THIS ASSIGNMENT
    this.orders = orders;
    this.payments = payments;
    this.shipping = shipping;
    this.inventory = inventory;
    this.retryPolicy = retryPolicy;
    this.circuitBreaker = circuitBreaker;
}
```

---

## Step 2: Add Event Publishing Methods

**Add these methods to the OrderOrchestrator class** (at the end, before the closing brace):

```java
// ===== EVENT PUBLISHING METHODS =====

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

---

## Step 3: Update placeOrder() Method

**Find this section in placeOrder()**:
```java
order.markPaid();
transactions.execute(() -> {
    orders.save(order);
});
```

**Add after it**:
```java
// ✅ PUBLISH PAYMENT SUCCESS EVENT
publishPaymentSuccessEvent(orderId, totalAmount, correlationId, idempotencyKey);
```

---

**Find this section in placeOrder()**:
```java
order.markShipmentRequested();
transactions.execute(() -> {
    orders.save(order);
});

return orderId;
```

**Replace with**:
```java
order.markShipmentRequested();
transactions.execute(() -> {
    orders.save(order);
});

// ✅ PUBLISH SHIPMENT REQUESTED EVENT
publishShipmentRequestedEvent(orderId, correlationId, idempotencyKey);

// ✅ PUBLISH ORDER PLACED EVENT (for email notification)
publishOrderPlacedEvent(orderId, order.getCustomerEmail(), correlationId);

return orderId;
```

---

## Step 4: Update Failure Handlers

**Find handlePaymentFailure() method and update it**:

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
```

**Find handleShipmentFailure() method and update it**:

```java
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

## Step 5: Add Import Statements

**Add these imports at the top of OrderOrchestrator.java**:

```java
import com.comp5348.messaging.config.RabbitMQConfig;
import com.comp5348.messaging.events.EventMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
```

---

## Verification Checklist

After making these changes:

- [ ] Code compiles without errors
- [ ] RabbitTemplate is injected in constructor
- [ ] Event publishing methods are added
- [ ] placeOrder() calls publishPaymentSuccessEvent()
- [ ] placeOrder() calls publishShipmentRequestedEvent()
- [ ] placeOrder() calls publishOrderPlacedEvent()
- [ ] handlePaymentFailure() calls publishPaymentFailedEvent()
- [ ] handlePaymentFailure() calls publishOrderCancelledEvent()
- [ ] handleShipmentFailure() calls publishOrderCancelledEvent()
- [ ] All imports are added
- [ ] Tests pass

---

## Testing

Run the application and check logs:

```
[OrderOrchestrator] ✅ Published payment.success event for order 12345 | Correlation: abc-123
[OrderOrchestrator] ✅ Published shipment.requested event for order 12345 | Correlation: abc-123
[OrderOrchestrator] ✅ Published order.placed event for order 12345 | Correlation: abc-123
```

If you see these logs, the implementation is correct! ✅

