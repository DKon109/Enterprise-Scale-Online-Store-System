# Comprehensive End-to-End Test Requirements

Based on COMP5348 assignment requirements, this document lists ALL E2E tests needed to verify the system works correctly.

---
## 0. Postman Setup and Global Conventions

- Workspace: Enterprise Software COMP5348 Workspace (use your existing team workspace)
- Collection: Update your existing E2E collection rather than creating a new one
- Environment variables (collection/environment scope):
  - STORE_BASE_URL
  - BANK_BASE_URL
  - DELIVERYCO_BASE_URL
  - EMAIL_BASE_URL
  - DEMO_USERNAME = customer
  - DEMO_PASSWORD = COMP5348
  - PRODUCT_SKU_1, PRODUCT_QTY
  - ORDER_ID, PAYMENT_ID, SHIPMENT_ID (captured during tests)
  - IDEMPOTENCY_KEY, CORRELATION_ID (persist across retries)
- Collection-level headers to apply to ALL state-changing requests:
  - X-Request-ID: {{IDEMPOTENCY_KEY}}
  - X-Correlation-ID: {{CORRELATION_ID}}
  - Content-Type: application/json
- Pre-request scripting (recommended):
  - If {{IDEMPOTENCY_KEY}} or {{CORRELATION_ID}} are empty, generate UUIDv4 once and persist to the environment.
  - For idempotency/retry tests, DO NOT regenerate these values between the original request and its retry; reuse the same values.
- Assertions style:
  - Use Postman Tests to assert HTTP status codes, required fields, and that downstream callbacks include the propagated headers/IDs in logs or payload echoes.
- Real integrations:
  - All adapters (Bank, DeliveryCo, EmailService) must call real HTTP endpoints (no in-memory mocks). Ensure the BASE_URL variables point to running services.


## 1. AUTHENTICATION TESTS (4 tests)

### 1.1 Demo Login (Username/Password)
- **Test:** Login with {{DEMO_USERNAME}}/{{DEMO_PASSWORD}}
- **Expected:** 200 OK; authentication established (session cookie or token)
- **Verify:** Subsequent authenticated request succeeds; do not log credentials

### 1.2 Invalid Login Rejected
- **Test:** Attempt login with incorrect password
- **Expected:** 401 Unauthorized
- **Verify:** No session/token issued; error body sanitized (no sensitive info)

### 1.3 Password Stored Hashed (non-HTTP verification)
- **Test:** Verify password hashes are stored (via integration test or admin diagnostic)
- **Expected:** Database stores salted hash (e.g., bcrypt), never plaintext
- **Verify:** Hash algorithm documented; plaintext password not retrievable

### 1.4 Multi-User Login
- **Test:** Login with an additional pre-seeded non-demo user
- **Expected:** 200 OK
- **Verify:** Independent session/token; multiple users supported beyond demo

---

## 2. ORDER PLACEMENT & FULFILLMENT (4 tests)

### 2.1 Complete Order Flow: Create → Reserve → Pay → Ship
- **Test:** Place order with valid customer, item, quantity
- **Steps:**
  1. Create order (PENDING)
  2. Reserve stock from warehouse (RESERVED)
  3. Authorize payment with Bank (PAID)
  4. Request shipment from DeliveryCo (SHIPMENT_REQUESTED)
- **Expected:** Order transitions through all states successfully
- **Verify:**
  - Order status: PENDING → RESERVED → PAID → SHIPMENT_REQUESTED
  - Stock reserved in inventory
  - Payment authorized (Auth+Capture)
  - Shipment requested with warehouse allocation
  - All calls include X-Request-ID and X-Correlation-ID headers

### 2.2 Order Fulfilled from Single Warehouse
- **Test:** Place order, stock allocated from one warehouse
- **Expected:** Order completes successfully
- **Verify:** Fulfillment created with correct warehouse ID

### 2.3 Order Fulfilled from Multiple Warehouses
- **Test:** Place order, stock allocated from multiple warehouses
- **Expected:** Order completes successfully
- **Verify:** Multiple fulfillments created, one per warehouse

### 2.4 Delivery Request Logged
- **Test:** Place order and inspect delivery request record
- **Expected:** Delivery status includes initial "Delivery request received"
- **Verify:** Delivery record persisted before shipment progression begins; status and timestamps recorded

---

## 3. DELIVERY LIFECYCLE & NOTIFICATIONS (3 tests)

### 3.1 Delivery Status: "Delivery request received" → "Picked up from warehouse(s)" → "In transit/on the way" → "Delivered"
- **Test:** Simulate DeliveryCo webhooks for each exact stage
- **Expected:** Status transitions follow the naming in the assignment; handler is idempotent
- **Verify:** Persisted delivery status names (or canonical codes mapping to these labels) and timestamps; webhook payloads idempotently processed

### 3.2 Delivery Status Email Notifications
- **Test:** Verify emails sent at these stages:
  - Picked up from warehouse(s) (now at depot)
  - On the delivery truck (in transit)
  - Delivery complete
- **Expected:** Emails are produced via the outbox worker (not inline)
- **Verify:** Outbox records created, worker delivers with retry/backoff, no duplicates on retry

### 3.3 Stock Committed After Pickup
- **Test:** Trigger the PICKED_UP webhook for an order
- **Expected:** Warehouse stock is deducted only after PICKED_UP
- **Verify:** Inventory commit occurs after webhook processing; prior to pickup, reservation remains

---

## 4. FAILURE SCENARIOS IN ORDER WORKFLOW (8 tests)

### 4.1 Stock Reservation Failure (2 tests)

#### 4.1.1 Order Cancelled When Stock Unavailable
- **Test:** Place order for item with insufficient stock
- **Expected:** 201 Created, order status CANCELLED
- **Verify:**
  - Order created in PENDING state
  - Stock reservation fails
  - Order transitions to CANCELLED
  - Notification sent to customer
  - Payment NOT called
  - Shipment NOT called

#### 4.1.2 Verify Stock Not Reserved
- **Test:** Check inventory after failed reservation
- **Expected:** Stock count unchanged
- **Verify:** No stock was allocated

### 4.2 Payment Authorization Failure (3 tests)

#### 4.2.1 Order Cancelled When Payment Fails
- **Test:** Place order but Bank rejects payment
- **Expected:** 201 Created, order status CANCELLED
- **Verify:**
  - Order created and stock reserved (RESERVED state)
  - Payment authorization fails
  - Order transitions to CANCELLED
  - Stock released back to inventory
  - Notification sent to customer
  - Shipment NOT called

#### 4.2.2 Verify Stock Released After Payment Failure
- **Test:** Check inventory after payment failure
- **Expected:** Stock returned to available pool
- **Verify:** Inventory.release() was called

#### 4.2.3 Verify Payment Not Captured
- **Test:** Check Bank records
- **Expected:** Payment only authorized, NOT captured
- **Verify:** Auth+Capture terminology: Auth failed, no Capture

### 4.3 Shipment Request Failure (3 tests)

#### 4.3.1 Order Cancelled When DeliveryCo Rejects
- **Test:** Place order but DeliveryCo rejects shipment
- **Expected:** 201 Created, order status PAID (not SHIPMENT_REQUESTED)
- **Verify:**
  - Order created, stock reserved, payment authorized (PAID state)
  - Shipment request fails
  - Order transitions to CANCELLED
  - Stock released back to inventory
  - Payment refunded to customer
  - Notification sent to customer

#### 4.3.2 Verify Stock Released After Shipment Failure
- **Test:** Check inventory after shipment failure
- **Expected:** Stock returned to available pool
- **Verify:** Inventory.release() was called

#### 4.3.3 Verify Payment Refunded After Shipment Failure
- **Test:** Check Bank records
- **Expected:** Payment refunded
- **Verify:** Bank.refund() was called

---

## 5. CANCELLATION PATHS (2 tests)

### 5.1 Cancel Order in PENDING State
- **Test:** Cancel order before any processing
- **Expected:** 204 No Content, order CANCELLED
- **Verify:** Stock not reserved, payment not charged

### 5.2 Cancel Order in PAID State
- **Test:** Cancel order after payment but before shipment
- **Expected:** 204 No Content, order CANCELLED
- **Verify:** Stock released, payment refunded

---

## 6. RESILIENCE & FAULT TOLERANCE (3 tests)

### 6.1 Retry with Exponential Backoff
- **Test:** External service fails, then succeeds
- **Expected:** Order completes after retries
- **Verify:** Retry logic with backoff works

### 6.2 Circuit Breaker Opens After Failures
- **Test:** External service fails multiple times
- **Expected:** Circuit breaker opens, fast fail
- **Verify:** Circuit breaker prevents cascading failures

### 6.3 Graceful Degradation
- **Test:** External service timeout
- **Expected:** Order cancelled gracefully
- **Verify:** No system crash, proper error handling

---

## 7. IDEMPOTENCY & CORRELATION (5 tests)

### 7.1 Idempotent First Request
- **Test:** Place order with X-Request-ID header
- **Expected:** 201 Created, order created
- **Verify:** Order ID returned, all services called

### 7.2 Idempotent Retry - Same Request ID
- **Test:** Retry same order with same X-Request-ID
- **Expected:** 201 Created, SAME order ID returned
- **Verify:** No duplicate order created, idempotency works

### 7.3 Idempotency Keys Propagated to External Calls
- **Test:** Place order and inspect outbound requests to Bank, Inventory, DeliveryCo
- **Expected:** Each call carries idempotency key unique to order/step
- **Verify:** Headers/body include idempotency keys for authorize/reserve/ship requests

### 7.4 Correlation ID Propagation
- **Test:** Place order with X-Correlation-ID header
- **Expected:** All external service calls include same Correlation-ID
- **Verify:** Logs show correlation chain

### 7.5 Request ID Propagation
- **Test:** Place order with X-Request-ID header
- **Expected:** All external service calls include same Request-ID
- **Verify:** Logs show request chain

---

## 8. OUTBOX & EMAIL WORKER RELIABILITY (2 tests)

### 8.1 Email Queued in Outbox (Not Sent Inline)
- **Test:** Place order, check outbox table
- **Expected:** Email record in outbox table
- **Verify:** Email NOT sent inline, queued for worker

### 8.2 Email Worker Processes Outbox
- **Test:** Wait for email worker to process
- **Expected:** Email sent from outbox
- **Verify:** Email service called, outbox record marked sent, worker retries with backoff on transient failure without duplicating messages

---

## 9. TIMELINE & AUDIT LOGGING (2 tests)

### 9.1 Order Timeline Captures State Changes
- **Test:** Execute happy-path order and read order_events timeline
- **Expected:** Timeline contains entries for each state transition with timestamps
- **Verify:** Event log persisted with correlation ID and actor metadata

### 9.2 Failure Scenario Logged with Compensation Steps
- **Test:** Force shipment failure and inspect timeline/audit log
- **Expected:** Events recorded for failure, refund, stock release, cancellation
- **Verify:** Log entries include correlation ID, failure reason, compensation actions

---

## Postman MCP Implementation Map

- Use your existing team workspace and existing E2E collection. The following structure and metadata enable automated updates via Postman MCP.

### Collection folder structure
1. 0-Setup
2. 1-Authentication
3. 2-Order Placement & Fulfillment
4. 3-Delivery Lifecycle & Notifications
5. 4-Failure Scenarios
6. 5-Cancellation Paths
7. 6-Resilience & Fault Tolerance
8. 7-Idempotency & Correlation
9. 8-Outbox & Email Worker
10. 9-Timeline & Logging

### Request metadata template (apply to each test)
- name: <Test number and title>
- method: <GET|POST|PUT|DELETE>
- url: <e.g., {{STORE_BASE_URL}}/api/orders>
- headers (add/merge at request-level in addition to collection-level):
  - X-Request-ID: {{IDEMPOTENCY_KEY}}
  - X-Correlation-ID: {{CORRELATION_ID}}
  - Content-Type: application/json
- body (if applicable): JSON using environment variables (e.g., sku: {{PRODUCT_SKU_1}}, quantity: {{PRODUCT_QTY}})
- tests: Postman test scripts asserting status codes, required fields; capture IDs into env vars (ORDER_ID, PAYMENT_ID, SHIPMENT_ID)

### Example mappings
- 1.1 Demo Login
  - name: 1.1 Demo Login
  - method: POST
  - url: {{STORE_BASE_URL}}/api/auth/login
  - body: { "username": "{{DEMO_USERNAME}}", "password": "{{DEMO_PASSWORD}}" }
  - tests: assert 200; if session cookie or token returned, store in env for subsequent requests

- 2.1 Create → Reserve → Pay → Ship (may be split into multiple requests)
  - Create Order: POST {{STORE_BASE_URL}}/api/orders
  - Reserve: POST {{STORE_BASE_URL}}/api/orders/{{ORDER_ID}}/reserve
  - Pay (Auth+Capture): POST {{BANK_BASE_URL}}/api/payments
  - Ship: POST {{DELIVERYCO_BASE_URL}}/api/shipments
  - tests: assert each step; capture ORDER_ID, PAYMENT_ID, SHIPMENT_ID; verify headers present

- 3.1 Delivery Status Progression (webhook simulation)
  - endpoints (DeliveryCo → Store webhooks):
    - POST {{STORE_BASE_URL}}/api/webhooks/delivery/request-received
    - POST {{STORE_BASE_URL}}/api/webhooks/delivery/picked-up
    - POST {{STORE_BASE_URL}}/api/webhooks/delivery/in-transit
    - POST {{STORE_BASE_URL}}/api/webhooks/delivery/delivered
  - tests: assert idempotent processing and persisted status names

- 5.2 Cancel Order in PAID State
  - method: POST
  - url: {{STORE_BASE_URL}}/api/orders/{{ORDER_ID}}/cancel
  - tests: assert 204; stock released; payment refunded (via BANK_BASE_URL verification request)

### Global pre-request script expectations
- Generate UUIDv4 for {{IDEMPOTENCY_KEY}} and {{CORRELATION_ID}} only when empty; persist to environment
- For idempotency tests, freeze these values between original and retry

### Monitors (optional)
- Create monitors for smoke paths (auth, happy order, webhook flow) with low frequency; use existing collection and environment

## TOTAL TEST COUNT: 33+ Tests

| Category | Count |
|----------|-------|
| Authentication | 4 |
| Order Placement & Fulfillment | 4 |
| Delivery Lifecycle & Notifications | 3 |
| Failure Scenarios | 8 |
| Cancellation Paths | 2 |
| Resilience & Fault Tolerance | 3 |
| Idempotency & Correlation | 5 |
| Outbox & Email Worker | 2 |
| Timeline & Logging | 2 |
| **TOTAL** | **33** |

---

## COMPLIANCE CHECKLIST

✅ Email outbox-driven with worker (not inline)
✅ 2+ explicit failure scenarios (payment, delivery)
✅ Auth+Capture terminology for Bank transfers
✅ "Delivery request received" status explicit
✅ Pre-shipment cancellation path
✅ Idempotency-Key and CorrelationId on all external calls
✅ Multi-warehouse allocations documented
✅ Outbox pattern, retries, circuit breaker, idempotency, correlation IDs, logging

---

## NON-FUNCTIONAL REQUIREMENTS COVERAGE

- **Availability & Resilience:** Sections 4 and 6 exercise failure handling, retries, circuit breaker behaviour, and graceful degradation under partial outages.
- **Consistency & Data Integrity:** Sections 2, 3, 5, 7, and 9 verify stock reservation/commitment rules, refund and release compensation, idempotency keys, correlation IDs, and immutable timelines.
- **Observability & Logging:** Sections 3.1, 7, and 9 confirm propagation of tracing headers and persistence of timeline/audit events.
- **Messaging & Outbox Reliability:** Section 8 proves asynchronous notification delivery with retry/backoff.
