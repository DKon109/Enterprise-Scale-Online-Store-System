# RabbitMQ Integration Guide - COMP5348

## Current Problem

RabbitMQ is **not being used effectively**:
- Store publishes events to queues ✅
- Listeners exist but only log messages ❌
- Real work still happens via direct HTTP calls ❌
- This violates §79-80 (Reliable asynchronous messaging)

## Correct Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ SYNCHRONOUS PHASE (OrderOrchestrator)                       │
├─────────────────────────────────────────────────────────────┤
│ 1. Reserve Stock (Inventory Service)                        │
│ 2. Authorize Payment (Bank Service)                         │
│ 3. Request Shipment (Delivery Service)                      │
│ 4. Save Order to Database                                   │
│ 5. Publish Events to RabbitMQ                               │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ ASYNCHRONOUS PHASE (Message Listeners)                      │
├─────────────────────────────────────────────────────────────┤
│ BankMessageListener                                         │
│   └─ Listens to: bank_queue                                 │
│   └─ Processes: payment.success, payment.failed events      │
│   └─ Action: Logs payment status (Bank already processed)   │
│                                                              │
│ WarehouseMessageListener                                    │
│   └─ Listens to: warehouse_queue                            │
│   └─ Processes: item.preparing, item.shipped events         │
│   └─ Action: Updates order status                           │
│                                                              │
│ EmailMessageListener                                        │
│   └─ Listens to: email_queue                                │
│   └─ Processes: order.placed, order.shipped events          │
│   └─ Action: Publishes to Outbox for reliable delivery      │
└─────────────────────────────────────────────────────────────┘
```

## Key Principles

### 1. **Synchronous vs Asynchronous**
- **Synchronous** (OrderOrchestrator): Call external services directly via HTTP
  - Reserve stock
  - Authorize payment
  - Request shipment
  - These MUST succeed before order is confirmed

- **Asynchronous** (Message Listeners): React to events after order is confirmed
  - Log payment status
  - Update order status
  - Send emails
  - These can fail and retry without blocking the order

### 2. **Event Publishing**
OrderOrchestrator publishes events AFTER successful operations:
```java
// After payment authorized
EventMessage paymentEvent = new EventMessage(
    "payment.success",
    orderId,
    amount,
    customerEmail,
    "Payment authorized"
);
rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, paymentEvent);
```

### 3. **Message Listener Pattern**
Listeners should:
1. Extract data from EventMessage
2. Call appropriate service methods
3. Handle errors gracefully
4. Log with correlation ID

```java
@RabbitListener(queues = "bank_queue")
public void onMessage(EventMessage event) {
    try {
        switch (event.getType()) {
            case "payment.success" -> 
                handlePaymentSuccess(event.getOrderId(), event.getAmount());
            case "payment.failed" -> 
                handlePaymentFailure(event.getOrderId());
        }
    } catch (Exception e) {
        log.error("Error processing bank event: {}", event, e);
        // Message will be retried via DLQ
    }
}
```

### 4. **Email Notifications (Outbox Pattern)**
EmailMessageListener should NOT call Email service directly.
Instead, it should write to Outbox table:
```java
@RabbitListener(queues = "email_queue")
public void onMessage(EventMessage event) {
    // Write to Outbox table (not send directly)
    OutboxEvent outboxEvent = new OutboxEvent(
        event.getOrderId(),
        event.getType(),
        event.getCustomerEmail(),
        event.getDescription(),
        false  // not sent yet
    );
    outboxRepository.save(outboxEvent);
    // OutboxWorker will process this later
}
```

## Implementation Steps

### Step 1: Update EventMessage
Add fields needed by listeners:
- `correlationId` - For tracing
- `timestamp` - When event occurred
- `retryCount` - For retry logic

### Step 2: Update OrderOrchestrator
After each successful operation, publish events:
- After payment authorized → publish "payment.success"
- After shipment requested → publish "shipment.requested"
- On payment failure → publish "payment.failed"

### Step 3: Update Message Listeners
Replace logging with actual business logic:
- BankMessageListener: Log payment status
- WarehouseMessageListener: Update order status
- EmailMessageListener: Write to Outbox table

### Step 4: Create Outbox Infrastructure
- OutboxEvent entity
- OutboxRepository
- OutboxWorker (scheduled task)

## Testing Strategy

1. **Unit Tests**: Test each listener in isolation
2. **Integration Tests**: Test full flow with RabbitMQ
3. **E2E Tests**: Test complete order flow with all services
4. **Failure Tests**: Test retry logic and DLQ handling

## Compliance Mapping

| Requirement | Implementation |
|-------------|-----------------|
| §79-80: Reliable messaging | Message listeners process events |
| §77: Data integrity | Outbox pattern for emails |
| §71-83: Fault tolerance | DLQ + retry logic |
| §242: Idempotency | Correlation ID in events |
| §246: Correlation tracking | Correlation ID propagated |

