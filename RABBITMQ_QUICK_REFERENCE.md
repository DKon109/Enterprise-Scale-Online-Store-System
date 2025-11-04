# RabbitMQ Quick Reference - COMP5348

## The Problem (Before)
```
❌ RabbitMQ configured but not used
❌ Listeners only log messages
❌ Real work still happens via HTTP
❌ Violates §79-80 (Reliable messaging)
```

## The Solution (After)
```
✅ OrderOrchestrator publishes events
✅ Listeners process events asynchronously
✅ Email uses Outbox pattern
✅ Complies with §79-80
```

---

## Event Flow (One Picture)

```
1. Customer places order
   ↓
2. OrderOrchestrator calls Bank (HTTP) ✅
   ↓
3. OrderOrchestrator publishes payment.success event
   ↓
4. BankMessageListener receives event
   ├─ Logs payment status
   └─ Updates audit trail
   ↓
5. OrderOrchestrator calls Delivery (HTTP) ✅
   ↓
6. OrderOrchestrator publishes shipment.requested event
   ↓
7. WarehouseMessageListener receives event
   ├─ Updates order status
   └─ Triggers warehouse fulfillment
   ↓
8. OrderOrchestrator publishes order.placed event
   ↓
9. EmailMessageListener receives event
   ├─ Writes to Outbox table
   └─ Does NOT send email yet
   ↓
10. OutboxWorker (every 5 seconds)
    ├─ Reads Outbox table
    ├─ Calls Email service (HTTP) ✅
    └─ Marks as sent
```

---

## Code Changes Summary

### EventMessage (UPDATED)
```java
@Data
public class EventMessage {
    private String type;              // "payment.success"
    private UUID orderId;             // Order ID
    private Double amount;            // Amount
    private String customerEmail;     // Email
    private String description;       // Description
    private String correlationId;     // ← NEW: For tracing
    private String idempotencyKey;    // ← NEW: For idempotency
    private LocalDateTime timestamp;  // ← NEW: When created
    private int retryCount;           // ← NEW: Retry tracking
}
```

### BankMessageListener (UPDATED)
```java
@RabbitListener(queues = "bank_queue")
public void onMessage(EventMessage event) {
    switch (event.getType()) {
        case "payment.success" -> handlePaymentSuccess(event);
        case "payment.failed" -> handlePaymentFailure(event);
        case "refund.completed" -> handleRefundCompleted(event);
    }
}

private void handlePaymentSuccess(EventMessage event) {
    log.info("[Bank] ✅ Payment SUCCESS for order {}", event.getOrderId());
    // Log status, update metrics, etc.
}
```

### WarehouseMessageListener (UPDATED)
```java
@RabbitListener(queues = "warehouse_queue")
public void onMessage(EventMessage event) {
    switch (event.getType()) {
        case "item.delivered" -> {
            log.info("[Warehouse] ✅ Item DELIVERED for order {}", event.getOrderId());
            orderOrchestrator.markDelivered(event.getOrderId());
        }
    }
}
```

### EmailMessageListener (UPDATED)
```java
@RabbitListener(queues = "email_queue")
public void onMessage(EventMessage event) {
    // Write to Outbox table (NOT sending email directly)
    OutboxEvent outboxEvent = new OutboxEvent(
        event.getOrderId(),
        event.getType(),
        event.getCustomerEmail(),
        event.getDescription(),
        false  // not sent yet
    );
    outboxRepository.save(outboxEvent);
    // OutboxWorker will send email later
}
```

### OrderOrchestrator (TODO)
```java
public UUID placeOrder(UUID customerId, String itemId, int quantity, String correlationId) {
    // ... existing code ...
    
    // After payment authorized
    order.markPaid();
    orders.save(order);
    publishPaymentSuccessEvent(orderId, amount, correlationId, idempotencyKey); // ← ADD
    
    // ... existing code ...
    
    // After shipment requested
    order.markShipmentRequested();
    orders.save(order);
    publishShipmentRequestedEvent(orderId, correlationId, idempotencyKey); // ← ADD
    
    return orderId;
}

private void publishPaymentSuccessEvent(UUID orderId, Money amount, String correlationId, String idempotencyKey) {
    EventMessage event = new EventMessage("payment.success", orderId, "Payment authorized");
    event.setAmount(amount.amount().doubleValue());
    event.setCorrelationId(correlationId);
    event.setIdempotencyKey(idempotencyKey);
    rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, event);
}
```

---

## Key Principles

| Principle | Explanation |
|-----------|-------------|
| **Synchronous First** | OrderOrchestrator calls external services via HTTP |
| **Async Events** | After success, publish events to RabbitMQ |
| **Listener Processing** | Listeners react to events, don't call external services |
| **Outbox for Email** | Write to Outbox table, let worker send emails |
| **Correlation ID** | Propagate through all events for tracing |
| **Error Handling** | Listeners throw exceptions, DLQ handles retries |

---

## Testing Checklist

- [ ] OrderOrchestrator publishes events after each operation
- [ ] BankMessageListener logs payment status
- [ ] WarehouseMessageListener updates order status
- [ ] EmailMessageListener writes to Outbox table
- [ ] OutboxWorker sends emails every 5 seconds
- [ ] Correlation ID appears in all logs
- [ ] Idempotency key prevents duplicate processing
- [ ] DLQ retries failed messages
- [ ] Complete order flow works end-to-end

---

## Files to Review

1. **RABBITMQ_INTEGRATION_GUIDE.md** - Architecture overview
2. **RABBITMQ_IMPLEMENTATION_EXAMPLES.md** - Code examples
3. **ORDERORCHESTRATOR_EVENT_PUBLISHING.md** - Implementation details
4. **RABBITMQ_FAQ_AND_ANSWERS.md** - Q&A
5. **RABBITMQ_ARCHITECTURE_SUMMARY.md** - Visual diagrams

---

## Compliance

✅ §79-80: Reliable asynchronous messaging
✅ §77: Data integrity (Outbox pattern)
✅ §71-83: Fault tolerance (DLQ + retries)
✅ §242: Idempotency (idempotencyKey in events)
✅ §246: Correlation tracking (correlationId propagated)

---

## Next Steps

1. Update OrderOrchestrator to publish events
2. Create Outbox infrastructure
3. Test with Postman
4. Verify logs show correlation IDs
5. Test failure scenarios

