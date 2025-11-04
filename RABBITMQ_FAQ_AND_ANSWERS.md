# RabbitMQ Integration - FAQ & Answers

## Your Questions Answered

### Q1: Should listeners call HTTP services directly, or invoke local service methods?

**Answer**: **Neither directly**. Here's the correct pattern:

```
OrderOrchestrator (Synchronous)
  ├─ Call Bank service via HTTP ✅
  ├─ Call Delivery service via HTTP ✅
  ├─ Save order to DB ✅
  └─ Publish events to RabbitMQ ✅
       ↓
Listeners (Asynchronous)
  ├─ BankMessageListener: Log payment status (no HTTP calls)
  ├─ WarehouseMessageListener: Update order status (call local OrderOrchestrator)
  └─ EmailMessageListener: Write to Outbox table (no HTTP calls)
```

**Key Point**: 
- **Synchronous phase** (OrderOrchestrator): Make HTTP calls to external services
- **Asynchronous phase** (Listeners): React to events, don't make HTTP calls

**Why?**
- Prevents duplicate HTTP calls
- Ensures order is confirmed before listeners run
- Allows listeners to fail without blocking the order

---

### Q2: How should this integrate with the outbox pattern for reliable delivery?

**Answer**: The outbox pattern is specifically for **email notifications**:

```
EmailMessageListener receives event
  ↓
Write to Outbox table (NOT sending email yet)
  ├─ orderId
  ├─ eventType
  ├─ customerEmail
  ├─ description
  ├─ sent = false
  └─ retryCount = 0
  ↓
OutboxWorker (runs every 5 seconds)
  ├─ Query Outbox for unsent records
  ├─ For each record:
  │  ├─ Call Email service via HTTP
  │  ├─ If success: Mark as sent
  │  └─ If failure: Increment retry count
  └─ If max retries exceeded: Move to DLQ
```

**Why Outbox for Email?**
- Email service might be temporarily unavailable
- If we send directly and fail, the message is lost
- Outbox ensures we retry until success

**Why NOT Outbox for Bank/Warehouse?**
- Bank/Warehouse operations are already done synchronously
- We're just logging the status asynchronously
- No need to retry logging

---

### Q3: Do I need to refactor OrderOrchestrator to publish events instead of making synchronous HTTP calls?

**Answer**: **No, keep synchronous HTTP calls**. Add event publishing AFTER:

```java
// KEEP THIS (Synchronous)
PaymentResult paymentResult = payments.authorize(orderId, amount, idempotencyKey);

if (!paymentResult.isAuthorized()) {
    handlePaymentFailure(order, paymentResult.reason(), correlationId);
    return orderId;
}

// ADD THIS (Asynchronous)
publishPaymentSuccessEvent(orderId, amount, correlationId, idempotencyKey);
```

**Why?**
- Order must be confirmed before listeners run
- If payment fails, we don't want listeners to process
- Synchronous calls ensure data consistency
- Asynchronous events enable monitoring/logging

---

### Q4: What's the correct flow: Store → RabbitMQ → Listeners → Services?

**Answer**: **Partially correct**. The full flow is:

```
┌─────────────────────────────────────────────────────────────┐
│ SYNCHRONOUS PHASE                                           │
├─────────────────────────────────────────────────────────────┤
│ Store (OrderOrchestrator)                                   │
│   ├─ Call Bank Service (HTTP) ✅                            │
│   ├─ Call Delivery Service (HTTP) ✅                        │
│   ├─ Save Order to DB ✅                                    │
│   └─ Publish Events to RabbitMQ ✅                          │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ ASYNCHRONOUS PHASE                                          │
├─────────────────────────────────────────────────────────────┤
│ RabbitMQ (Message Broker)                                   │
│   ├─ bank_queue                                             │
│   ├─ warehouse_queue                                        │
│   └─ email_queue                                            │
│         ↓                                                    │
│ Listeners (Message Consumers)                               │
│   ├─ BankMessageListener: Log status                        │
│   ├─ WarehouseMessageListener: Update order status          │
│   └─ EmailMessageListener: Write to Outbox                  │
│         ↓                                                    │
│ Outbox Worker (Scheduled Task)                              │
│   └─ Call Email Service (HTTP) ✅                           │
└─────────────────────────────────────────────────────────────┘
```

**Correct Flow**:
1. Store calls external services synchronously (HTTP)
2. Store publishes events to RabbitMQ
3. Listeners consume events asynchronously
4. Listeners update local state or write to Outbox
5. Outbox worker calls Email service (HTTP)

---

### Q5: How do I know if my implementation is correct?

**Answer**: Check these criteria:

```
✅ CORRECT if:
  ├─ OrderOrchestrator publishes events after each operation
  ├─ BankMessageListener logs payment status (no HTTP calls)
  ├─ WarehouseMessageListener updates order status
  ├─ EmailMessageListener writes to Outbox table
  ├─ OutboxWorker calls Email service every 5 seconds
  ├─ Correlation ID is propagated through all events
  ├─ Idempotency key is included in events
  ├─ Listeners throw exceptions on error (triggers DLQ)
  └─ All events are logged with correlation ID

❌ WRONG if:
  ├─ Listeners make HTTP calls to external services
  ├─ EmailMessageListener sends emails directly
  ├─ OrderOrchestrator doesn't publish events
  ├─ Correlation ID is not propagated
  ├─ Listeners only log messages (no business logic)
  └─ No Outbox table for email notifications
```

---

## Implementation Checklist

### Phase 1: Update Message Listeners (DONE)
- [x] Update EventMessage with correlationId, idempotencyKey, timestamp
- [x] Update BankMessageListener to process events
- [x] Update WarehouseMessageListener to process events
- [x] Update EmailMessageListener to use Outbox pattern

### Phase 2: Update OrderOrchestrator (TODO)
- [ ] Add RabbitTemplate dependency
- [ ] Add event publishing methods
- [ ] Call publishPaymentSuccessEvent() after payment authorized
- [ ] Call publishShipmentRequestedEvent() after shipment requested
- [ ] Call publishOrderPlacedEvent() after order confirmed
- [ ] Call publishPaymentFailedEvent() on payment failure
- [ ] Call publishOrderCancelledEvent() on order cancellation

### Phase 3: Create Outbox Infrastructure (TODO)
- [ ] Create OutboxEvent entity
- [ ] Create OutboxRepository
- [ ] Create OutboxWorker scheduled task
- [ ] Add Outbox table to database

### Phase 4: Testing (TODO)
- [ ] Unit tests for event publishing
- [ ] Unit tests for message listeners
- [ ] Integration tests for complete flow
- [ ] E2E tests with Postman
- [ ] Failure scenario tests

---

## Key Takeaways

1. **Synchronous = HTTP calls** (OrderOrchestrator)
2. **Asynchronous = Event listeners** (RabbitMQ)
3. **Email = Outbox pattern** (Reliable delivery)
4. **Correlation ID = Tracing** (Propagate everywhere)
5. **Errors = DLQ retry** (Automatic retry logic)

---

## Files Updated

- ✅ `src/main/java/com/comp5348/messaging/events/EventMessage.java`
- ✅ `src/main/java/com/comp5348/messaging/bank/BankMessageListener.java`
- ✅ `src/main/java/com/comp5348/messaging/warehouse/WarehouseMessageListener.java`
- ✅ `src/main/java/com/comp5348/messaging/email/EmailMessageListener.java`

## Files to Create

- 📝 `RABBITMQ_INTEGRATION_GUIDE.md` (Architecture overview)
- 📝 `RABBITMQ_IMPLEMENTATION_EXAMPLES.md` (Code examples)
- 📝 `RABBITMQ_ARCHITECTURE_SUMMARY.md` (Visual diagrams)
- 📝 `ORDERORCHESTRATOR_EVENT_PUBLISHING.md` (Implementation guide)
- 📝 `RABBITMQ_FAQ_AND_ANSWERS.md` (This file)

