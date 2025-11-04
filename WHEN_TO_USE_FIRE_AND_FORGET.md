# When to Use Fire and Forget

## The Short Answer

Fire and forget is used for **non-critical, best-effort notifications** where:
- Data loss is acceptable
- Duplicate processing is acceptable
- No consistency guarantee needed
- No audit trail required

**NOT for financial transactions, inventory, or critical business logic.**

---

## Appropriate Use Cases

### 1. Analytics and Logging

```java
// Fire and forget: OK ✅
public void trackUserAction(String userId, String action) {
    EventMessage event = new EventMessage(userId, "USER_ACTION", action);
    rabbitTemplate.convertAndSend(ANALYTICS_QUEUE, event);
    // Don't wait for confirmation
}
```

**Why it's OK:**
- If event is lost, analytics is slightly inaccurate (acceptable)
- No financial impact
- No business logic depends on it
- Duplicate events don't break anything

### 2. Non-Critical Notifications

```java
// Fire and forget: OK ✅
public void sendWelcomeEmail(String email) {
    EmailEvent event = new EmailEvent(email, "WELCOME");
    rabbitTemplate.convertAndSend(EMAIL_QUEUE, event);
    // Don't wait for confirmation
}
```

**Why it's OK:**
- If email is lost, user doesn't get welcome email (annoying but not critical)
- No financial impact
- User can still use the system
- Duplicate emails are annoying but not catastrophic

### 3. Audit Logging (Non-Critical)

```java
// Fire and forget: OK ✅
public void logUserLogin(String userId) {
    AuditEvent event = new AuditEvent(userId, "LOGIN");
    rabbitTemplate.convertAndSend(AUDIT_QUEUE, event);
    // Don't wait for confirmation
}
```

**Why it's OK:**
- If event is lost, audit log is incomplete (acceptable)
- No financial impact
- No business logic depends on it
- System still works

### 4. Metrics and Monitoring

```java
// Fire and forget: OK ✅
public void recordMetric(String metricName, double value) {
    MetricEvent event = new MetricEvent(metricName, value);
    rabbitTemplate.convertAndSend(METRICS_QUEUE, event);
    // Don't wait for confirmation
}
```

**Why it's OK:**
- If event is lost, metrics are slightly inaccurate (acceptable)
- No financial impact
- No business logic depends on it
- Duplicate metrics don't break anything

### 5. Cache Invalidation

```java
// Fire and forget: OK ✅
public void invalidateCache(String cacheKey) {
    CacheEvent event = new CacheEvent(cacheKey, "INVALIDATE");
    rabbitTemplate.convertAndSend(CACHE_QUEUE, event);
    // Don't wait for confirmation
}
```

**Why it's OK:**
- If event is lost, cache is stale (acceptable, will refresh on next request)
- No financial impact
- No business logic depends on it
- System still works

---

## NOT Appropriate Use Cases

### ❌ Financial Transactions

```java
// Fire and forget: NOT OK ❌
public UUID processPayment(UUID orderId, BigDecimal amount) {
    // Save order
    Order order = new Order(orderId, amount);
    orderRepository.save(order);
    
    // Fire and forget: WRONG ❌
    PaymentEvent event = new PaymentEvent(orderId, amount);
    rabbitTemplate.convertAndSend(BANK_QUEUE, event);
    
    return orderId;
}
```

**Why it's NOT OK:**
- If event is lost, payment never processed
- Financial impact: Customer charged but order not fulfilled
- Business logic depends on it
- Duplicate payments cause double charging

### ❌ Inventory Management

```java
// Fire and forget: NOT OK ❌
public void reserveInventory(UUID orderId, Long itemId, int quantity) {
    // Fire and forget: WRONG ❌
    InventoryEvent event = new InventoryEvent(orderId, itemId, quantity);
    rabbitTemplate.convertAndSend(WAREHOUSE_QUEUE, event);
}
```

**Why it's NOT OK:**
- If event is lost, inventory never reserved
- Business logic depends on it
- Duplicate reservations cause overselling
- Inconsistent state between database and warehouse

### ❌ Order Processing

```java
// Fire and forget: NOT OK ❌
public UUID placeOrder(UUID orderId, Long itemId, int quantity) {
    // Fire and forget: WRONG ❌
    OrderEvent event = new OrderEvent(orderId, itemId, quantity);
    rabbitTemplate.convertAndSend(ORDER_QUEUE, event);
}
```

**Why it's NOT OK:**
- If event is lost, order never processed
- Financial impact
- Business logic depends on it
- Duplicate orders cause duplicate charges

### ❌ Critical Notifications

```java
// Fire and forget: NOT OK ❌
public void notifyPaymentFailure(UUID orderId, String reason) {
    // Fire and forget: WRONG ❌
    NotificationEvent event = new NotificationEvent(orderId, reason);
    rabbitTemplate.convertAndSend(EMAIL_QUEUE, event);
}
```

**Why it's NOT OK:**
- If event is lost, customer never notified of payment failure
- Customer doesn't know order failed
- Customer support gets angry calls
- Business logic depends on it

---

## Decision Matrix

| Scenario | Fire and Forget | Outbox Pattern |
|----------|-----------------|----------------|
| **Analytics** | ✅ OK | ⭐ Better |
| **Non-critical notifications** | ✅ OK | ⭐ Better |
| **Audit logging** | ✅ OK | ⭐ Better |
| **Metrics** | ✅ OK | ⭐ Better |
| **Cache invalidation** | ✅ OK | ⭐ Better |
| **Financial transactions** | ❌ NO | ✅ REQUIRED |
| **Inventory management** | ❌ NO | ✅ REQUIRED |
| **Order processing** | ❌ NO | ✅ REQUIRED |
| **Critical notifications** | ❌ NO | ✅ REQUIRED |
| **Payment processing** | ❌ NO | ✅ REQUIRED |

---

## The Rule of Thumb

**Ask yourself:**

1. **If this event is lost, will the business be impacted?**
   - YES → Use Outbox Pattern ✅
   - NO → Fire and forget is OK ✅

2. **If this event is duplicated, will it cause problems?**
   - YES → Use Outbox Pattern ✅
   - NO → Fire and forget is OK ✅

3. **Does business logic depend on this event?**
   - YES → Use Outbox Pattern ✅
   - NO → Fire and forget is OK ✅

4. **Is this a financial transaction?**
   - YES → Use Outbox Pattern ✅
   - NO → Fire and forget might be OK ✅

---

## Examples by Category

### ✅ Fire and Forget is OK

```
Analytics Events
├─ User clicked button
├─ User viewed page
├─ User scrolled
└─ User spent X seconds

Logging Events
├─ User logged in
├─ User logged out
├─ User changed password
└─ User updated profile

Metrics Events
├─ API response time
├─ Database query time
├─ Cache hit rate
└─ Error rate

Notifications (Non-Critical)
├─ Welcome email
├─ Newsletter
├─ Promotional email
└─ Reminder email
```

### ❌ Fire and Forget is NOT OK

```
Financial Events
├─ Payment authorized
├─ Payment captured
├─ Refund processed
└─ Balance updated

Inventory Events
├─ Stock reserved
├─ Stock committed
├─ Stock released
└─ Stock updated

Order Events
├─ Order placed
├─ Order confirmed
├─ Order shipped
└─ Order delivered

Critical Notifications
├─ Payment failed
├─ Order cancelled
├─ Delivery failed
└─ Stock unavailable
```

---

## COMP5348 Context

In COMP5348, you're building an online store with:

### Critical (Use Outbox Pattern)
- ✅ Payment events (Bank integration)
- ✅ Inventory events (Warehouse integration)
- ✅ Order events (Order processing)
- ✅ Delivery events (Delivery status)
- ✅ Critical notifications (Payment failed, Order cancelled)

### Non-Critical (Fire and Forget OK)
- ✅ Analytics (User behavior)
- ✅ Audit logging (Non-critical)
- ✅ Metrics (System monitoring)
- ✅ Non-critical notifications (Welcome email)

---

## Summary

**Fire and Forget is used for:**
- ✅ Analytics and logging
- ✅ Non-critical notifications
- ✅ Metrics and monitoring
- ✅ Cache invalidation
- ✅ Best-effort notifications

**Fire and Forget is NOT used for:**
- ❌ Financial transactions
- ❌ Inventory management
- ❌ Order processing
- ❌ Critical notifications
- ❌ Anything with business logic

**For COMP5348:**
- Use **Outbox Pattern** for all critical events (payments, inventory, orders)
- Use **Fire and Forget** for analytics and non-critical notifications
- When in doubt, use **Outbox Pattern** (it's always safer)

---

## Key Takeaway

**Fire and forget is NOT a bad pattern. It's just used for the wrong things.**

Use it for:
- Non-critical, best-effort notifications
- Analytics and monitoring
- Things where data loss is acceptable

Don't use it for:
- Financial transactions
- Inventory management
- Order processing
- Anything critical to business logic

**For COMP5348: Use Outbox Pattern for all critical events.**

