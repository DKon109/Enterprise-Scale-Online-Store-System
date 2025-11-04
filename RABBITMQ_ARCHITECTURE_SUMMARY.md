# RabbitMQ Architecture Summary - COMP5348

## Problem Statement

**Current State**: RabbitMQ is configured but not being used effectively
- ❌ Listeners only log messages
- ❌ Real work still happens via direct HTTP calls
- ❌ Violates §79-80 (Reliable asynchronous messaging)

**Solution**: Implement proper event-driven architecture

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│ SYNCHRONOUS PHASE (OrderOrchestrator)                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  1. Reserve Stock                                                │
│     └─ Call: InventoryService.reserve() [HTTP]                  │
│     └─ Result: Success or Failure                               │
│                                                                   │
│  2. Authorize Payment                                            │
│     └─ Call: BankService.authorize() [HTTP]                     │
│     └─ Result: Success or Failure                               │
│                                                                   │
│  3. Request Shipment                                             │
│     └─ Call: DeliveryService.request() [HTTP]                   │
│     └─ Result: Success or Failure                               │
│                                                                   │
│  4. Save Order to Database                                       │
│     └─ Order status: RESERVED → PAID → SHIPMENT_REQUESTED       │
│                                                                   │
│  5. Publish Events to RabbitMQ                                   │
│     └─ Event: payment.success → bank_queue                      │
│     └─ Event: shipment.requested → warehouse_queue              │
│     └─ Event: order.placed → email_queue                        │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────────┐
│ ASYNCHRONOUS PHASE (Message Listeners)                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  BankMessageListener (bank_queue)                                │
│  ├─ Event: payment.success                                       │
│  │  └─ Action: Log payment status                               │
│  │  └─ Purpose: Audit trail                                     │
│  ├─ Event: payment.failed                                        │
│  │  └─ Action: Log failure                                      │
│  │  └─ Purpose: Monitoring/alerts                               │
│  └─ Event: refund.completed                                      │
│     └─ Action: Log refund                                        │
│     └─ Purpose: Audit trail                                      │
│                                                                   │
│  WarehouseMessageListener (warehouse_queue)                      │
│  ├─ Event: shipment.requested                                    │
│  │  └─ Action: Log shipment request                             │
│  │  └─ Purpose: Warehouse fulfillment trigger                   │
│  ├─ Event: item.shipped                                          │
│  │  └─ Action: Update order status                              │
│  │  └─ Purpose: Order tracking                                  │
│  └─ Event: item.delivered                                        │
│     └─ Action: Call orderOrchestrator.markDelivered()           │
│     └─ Purpose: Complete order                                   │
│                                                                   │
│  EmailMessageListener (email_queue)                              │
│  ├─ Event: order.placed                                          │
│  │  └─ Action: Write to Outbox table                            │
│  │  └─ Purpose: Queue email for reliable delivery               │
│  ├─ Event: order.shipped                                         │
│  │  └─ Action: Write to Outbox table                            │
│  │  └─ Purpose: Queue email for reliable delivery               │
│  └─ Event: order.cancelled                                       │
│     └─ Action: Write to Outbox table                            │
│     └─ Purpose: Queue email for reliable delivery               │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────────┐
│ OUTBOX WORKER (Scheduled Task)                                   │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Every 5 seconds:                                                │
│  1. Query Outbox table for unsent records                        │
│  2. For each record:                                             │
│     ├─ Call Email Service [HTTP]                                │
│     ├─ If success: Mark as sent                                 │
│     └─ If failure: Increment retry count                        │
│  3. If max retries exceeded: Move to DLQ                         │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Examples

### Example 1: Successful Order Flow

```
1. Customer places order
   └─ POST /orders

2. OrderOrchestrator.placeOrder() called
   ├─ Reserve stock (HTTP) ✅
   ├─ Authorize payment (HTTP) ✅
   ├─ Request shipment (HTTP) ✅
   ├─ Save order to DB ✅
   └─ Publish events to RabbitMQ ✅

3. BankMessageListener receives payment.success
   └─ Log: "Payment authorized for order 12345"

4. WarehouseMessageListener receives shipment.requested
   └─ Log: "Shipment requested for order 12345"

5. EmailMessageListener receives order.placed
   └─ Write to Outbox table

6. OutboxWorker processes Outbox
   ├─ Call Email Service (HTTP) ✅
   └─ Mark as sent

Result: Order complete, customer notified ✅
```

### Example 2: Payment Failure Flow

```
1. Customer places order
   └─ POST /orders

2. OrderOrchestrator.placeOrder() called
   ├─ Reserve stock (HTTP) ✅
   ├─ Authorize payment (HTTP) ❌ FAILED
   └─ Publish payment.failed event

3. BankMessageListener receives payment.failed
   └─ Log: "Payment failed for order 12345"

4. OrderOrchestrator handles failure
   ├─ Release stock reservation
   ├─ Update order status to FAILED
   └─ Publish order.cancelled event

5. EmailMessageListener receives order.cancelled
   └─ Write to Outbox table

6. OutboxWorker processes Outbox
   ├─ Call Email Service (HTTP) ✅
   └─ Send cancellation email

Result: Order cancelled, customer notified ✅
```

---

## Key Differences: Before vs After

| Aspect | Before (WRONG) | After (CORRECT) |
|--------|----------------|-----------------|
| **Event Publishing** | ❌ No events published | ✅ Events published after each operation |
| **Listener Logic** | ❌ Only logging | ✅ Process events, update state |
| **Email Sending** | ❌ Direct HTTP calls | ✅ Outbox pattern for reliability |
| **Error Handling** | ❌ No retry logic | ✅ DLQ + exponential backoff |
| **Correlation Tracking** | ❌ Not propagated | ✅ Included in all events |
| **Compliance** | ❌ Violates §79-80 | ✅ Complies with §79-80 |

---

## Implementation Checklist

- [x] Update EventMessage with correlationId, idempotencyKey, timestamp
- [x] Update BankMessageListener to process events
- [x] Update WarehouseMessageListener to process events
- [x] Update EmailMessageListener to use Outbox pattern
- [ ] Update OrderOrchestrator to publish events
- [ ] Create OutboxEvent entity
- [ ] Create OutboxRepository
- [ ] Create OutboxWorker scheduled task
- [ ] Add tests for event publishing
- [ ] Add tests for message listeners
- [ ] Add tests for Outbox worker
- [ ] Update Postman collection with event verification

---

## Next Steps

1. **Update OrderOrchestrator** to publish events after each operation
2. **Create Outbox infrastructure** (entity, repository, worker)
3. **Test event flow** with Postman
4. **Verify correlation IDs** are propagated through logs
5. **Test failure scenarios** (payment fail, delivery fail)

