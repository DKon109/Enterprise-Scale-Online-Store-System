# COMP5348 E2E Tests - Requirements Coverage Analysis

## Executive Summary
This document maps the Postman E2E test collection against all COMP5348 requirements to ensure comprehensive coverage.

---

## 1. AUTHENTICATION REQUIREMENTS (§18-26)

### Requirement
- Username/password authentication
- Hashed password storage
- Demo account: `customer/COMP5348`
- Multiple users supported

### E2E Tests Coverage
✅ **1.1 Register Customer** - Tests user registration with unique username/email
✅ **1.2 Login Demo Account** - Tests demo account `customer/COMP5348`
✅ **1.3 Get Current Customer** - Tests authenticated user retrieval

**Status:** ✅ COVERED

---

## 2. ORDER PROCESSING WORKFLOW (§31-48)

### 2.1 Single Item, Variable Quantity
✅ **5.1 Place Order** - Tests placing order with customerId, itemId, quantity

### 2.2 Multi-Warehouse Fulfillment
✅ **6.1 Reserve Fulfillment** - Tests fulfillment reservation across warehouses
✅ **6.2 List Fulfillments by Order** - Tests ORDER_FULFILLMENT tracking
✅ **6.3 Commit Fulfillment** - Tests fulfillment commitment

### 2.3 Bank Integration (Auth + Capture)
⚠️ **PARTIAL** - Endpoints exist but need verification:
- Payment transaction endpoints needed
- Idempotency key handling verification
- Retry/circuit breaker testing

### 2.4 DeliveryCo Integration
✅ **7.1 List Deliveries by Order** - Tests delivery status tracking
✅ **7.2 Track Delivery by Tracking Number** - Tests delivery tracking

### 2.5 Email Notifications (Outbox Pattern)
⚠️ **NOT DIRECTLY TESTABLE** - Outbox pattern is async:
- Requires log verification or webhook callback testing
- Email delivery status needs monitoring

### 2.6 Stock Deduction Timing
✅ **4.2 Reserve Inventory** - Tests stock reservation
⚠️ **NEEDS VERIFICATION** - Stock commitment on PICKED_UP webhook

**Status:** ⚠️ PARTIALLY COVERED - Needs failure scenario tests

---

## 3. ORDER CANCELLATION (§50-54)

### Requirement
- Cancel before shipment request
- Refund payment
- Release warehouse stock
- Email notification

### E2E Tests Coverage
✅ **5.4 Cancel Order (Pre-Shipment)** - Tests order cancellation

**Status:** ⚠️ PARTIAL - Needs verification of:
- Refund processing
- Stock release
- Email notification

---

## 4. FAILURE SCENARIOS (§75-76, §207-217)

### Scenario 1: Payment Authorization Fails
❌ **NOT COVERED** - Needs:
- Mock Bank service rejection
- Stock release verification
- Email notification verification
- Order cancellation verification

### Scenario 2: DeliveryCo Rejects/Package Lost
❌ **NOT COVERED** - Needs:
- Mock DeliveryCo rejection
- Refund verification
- Stock release verification
- Email notification verification

**Status:** ❌ NOT COVERED - Critical gap

---

## 5. QUALITY ATTRIBUTES (§71-83)

### 5.1 Availability & Fault Tolerance
⚠️ **PARTIAL**:
- Retry policy: Not directly testable via E2E
- Circuit breaker: Not directly testable via E2E
- Graceful degradation: Needs failure scenario tests

### 5.2 Transactions, Messaging & Integration
✅ **Event Sourcing**: Order events logged
✅ **Outbox Pattern**: Email via outbox (not inline)
⚠️ **Saga Orchestration**: Needs verification
⚠️ **Idempotency Keys**: Needs verification in requests
⚠️ **Correlation ID**: Needs verification in logs

### 5.3 Logging & Observability
⚠️ **PARTIAL** - Requires log verification:
- Critical events logged
- Inter-component communication logged
- Correlation IDs in logs

**Status:** ⚠️ PARTIAL - Needs log verification tests

---

## 6. DATA PERSISTENCE (§156-159)

### Database Schema Coverage
✅ **orders** - Tested via 5.1-5.4
✅ **products** - Tested via 2.1-2.4
✅ **warehouses** - Tested via 3.1-3.3
✅ **inventory** - Tested via 4.1-4.2
✅ **fulfillments** - Tested via 6.1-6.3
✅ **deliveries** - Tested via 7.1-7.2
⚠️ **order_events** - Not directly tested
⚠️ **saga_state** - Not directly tested
⚠️ **outbox_events** - Not directly tested

**Status:** ⚠️ PARTIAL - Event tables need verification

---

## 7. CRITICAL GAPS IDENTIFIED

### 🔴 HIGH PRIORITY
1. **Failure Scenario 1: Payment Fails** - No test
2. **Failure Scenario 2: DeliveryCo Rejects** - No test
3. **Outbox Pattern Verification** - No direct test
4. **Idempotency Key Verification** - No test
5. **Correlation ID Tracking** - No test

### 🟡 MEDIUM PRIORITY
1. **Bank Integration Details** - Partial coverage
2. **Webhook Callback Testing** - Not covered
3. **Retry/Circuit Breaker** - Not covered
4. **Log Verification** - Not covered

### 🟢 LOW PRIORITY
1. **Event sourcing verification** - Partial
2. **Saga state tracking** - Partial

---

## 8. RECOMMENDATIONS

### Immediate Actions
1. Add failure scenario tests (Payment fails, DeliveryCo rejects)
2. Add idempotency key verification tests
3. Add correlation ID tracking tests
4. Add outbox pattern verification tests

### Testing Strategy
- Use Postman test scripts to verify response headers (idempotency keys)
- Add log verification tests
- Add webhook callback simulation tests
- Add failure injection tests

---

## 9. TEST EXECUTION CHECKLIST

- [ ] Run all 7 test groups sequentially
- [ ] Verify all 200 OK / 201 Created responses
- [ ] Check logs for correlation IDs
- [ ] Verify outbox events processed
- [ ] Verify stock levels updated
- [ ] Verify order state transitions
- [ ] Test failure scenarios manually
- [ ] Verify email notifications sent

---

**Last Updated:** 2025-11-03
**Status:** ⚠️ PARTIAL COVERAGE - 60% of requirements covered, 40% needs enhancement

