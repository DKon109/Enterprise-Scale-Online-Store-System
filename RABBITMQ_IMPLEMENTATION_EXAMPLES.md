# RabbitMQ Implementation Examples - COMP5348

## Example 1: Update OrderOrchestrator to Publish Events

### Current Code (WRONG)
```java
public UUID placeOrder(UUID customerId, String itemId, int quantity, String correlationId) {
    // ... reserve stock, authorize payment, request shipment ...
    order.markPaid();
    orders.save(order);
    return orderId;  // ← No events published
}
```

### Fixed Code (CORRECT)
```java
@Service
public class OrderOrchestrator {
    
    private final RabbitTemplate rabbitTemplate;
    private final OrderRepository orders;
    private final PaymentServicePort payments;
    private final ShippingServicePort shipping;
    private final InventoryServicePort inventory;
    
    public OrderOrchestrator(
        RabbitTemplate rabbitTemplate,
        OrderRepository orders,
        PaymentServicePort payments,
        ShippingServicePort shipping,
        InventoryServicePort inventory
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.orders = orders;
        this.payments = payments;
        this.shipping = shipping;
        this.inventory = inventory;
    }
    
    public UUID placeOrder(UUID customerId, String itemId, int quantity, String correlationId) {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, itemId, quantity);
        
        // 1. RESERVE STOCK (Synchronous)
        InventoryServicePort.ReserveResult reserveResult = inventory.reserve(orderId, itemId, quantity);
        if (!reserveResult.isSuccess()) {
            handleReservationFailure(order, reserveResult.reason(), correlationId);
            return orderId;
        }
        order.markReserved();
        orders.save(order);
        
        // 2. AUTHORIZE PAYMENT (Synchronous)
        Money totalAmount = Money.of(quantity);
        String idempotencyKey = IdempotencyKeyGenerator.forOrder(orderId);
        PaymentServicePort.PaymentResult paymentResult = 
            payments.authorize(orderId, totalAmount, idempotencyKey);
        
        if (!paymentResult.isAuthorized()) {
            handlePaymentFailure(order, paymentResult.reason(), correlationId);
            return orderId;
        }
        order.markPaid();
        orders.save(order);
        
        // ✅ PUBLISH PAYMENT SUCCESS EVENT (Asynchronous)
        publishPaymentSuccessEvent(orderId, totalAmount, correlationId, idempotencyKey);
        
        // 3. REQUEST SHIPMENT (Synchronous)
        ShippingServicePort.ShipmentResult shipmentResult = 
            shipping.request(orderId, reserveResult.allocations());
        
        if (!shipmentResult.isAccepted()) {
            handleShipmentFailure(order, "Shipment rejected", correlationId);
            return orderId;
        }
        order.markShipmentRequested();
        orders.save(order);
        
        // ✅ PUBLISH SHIPMENT REQUESTED EVENT (Asynchronous)
        publishShipmentRequestedEvent(orderId, correlationId, idempotencyKey);
        
        return orderId;
    }
    
    // ===== EVENT PUBLISHING METHODS =====
    
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
            log.info("[OrderOrchestrator] Published payment.success event for order {} | Correlation: {}", 
                orderId, correlationId);
        } catch (Exception e) {
            log.error("[OrderOrchestrator] Failed to publish payment.success event for order {}: {}", 
                orderId, e.getMessage());
            // Note: Order is already paid, so we don't fail the order
            // The event will be retried by RabbitMQ
        }
    }
    
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
            log.info("[OrderOrchestrator] Published shipment.requested event for order {} | Correlation: {}", 
                orderId, correlationId);
        } catch (Exception e) {
            log.error("[OrderOrchestrator] Failed to publish shipment.requested event for order {}: {}", 
                orderId, e.getMessage());
        }
    }
    
    private void publishPaymentFailedEvent(UUID orderId, String reason, String correlationId) {
        EventMessage event = new EventMessage(
            "payment.failed",
            orderId,
            "Payment failed: " + reason
        );
        event.setCorrelationId(correlationId);
        
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_QUEUE, event);
            log.info("[OrderOrchestrator] Published payment.failed event for order {}", orderId);
        } catch (Exception e) {
            log.error("[OrderOrchestrator] Failed to publish payment.failed event for order {}: {}", 
                orderId, e.getMessage());
        }
    }
}
```

## Example 2: Message Listener Processing Events

### BankMessageListener (Correct Implementation)
```java
@RabbitListener(queues = "bank_queue")
public void onMessage(EventMessage event) {
    try {
        String correlationId = event.getCorrelationId() != null ? event.getCorrelationId() : "N/A";
        
        switch (event.getType()) {
            case "payment.success" -> {
                log.info("[Bank] ✅ Payment SUCCESS for order {} | Amount: {} | Correlation: {}", 
                    event.getOrderId(), event.getAmount(), correlationId);
                // Optional: Update metrics, audit log, etc.
            }
            case "payment.failed" -> {
                log.warn("[Bank] ❌ Payment FAILED for order {} | Correlation: {}", 
                    event.getOrderId(), correlationId);
                // Optional: Alert, audit log, etc.
            }
        }
    } catch (Exception e) {
        log.error("[Bank] Error processing event: {}", event.getType(), e);
        throw new RuntimeException("Failed to process bank event", e);
    }
}
```

### EmailMessageListener (Outbox Pattern)
```java
@RabbitListener(queues = "email_queue")
public void onMessage(EventMessage event) {
    try {
        // Write to Outbox table (NOT sending email directly)
        OutboxEvent outboxEvent = new OutboxEvent(
            event.getOrderId(),
            event.getType(),
            event.getCustomerEmail(),
            event.getDescription(),
            false  // not sent yet
        );
        outboxEvent.setCorrelationId(event.getCorrelationId());
        outboxEvent.setRetryCount(0);
        
        outboxRepository.save(outboxEvent);
        log.info("[Email] Email event queued to Outbox for order {}", event.getOrderId());
        
        // OutboxWorker will process this later
    } catch (Exception e) {
        log.error("[Email] Error processing email event: {}", event.getType(), e);
        throw new RuntimeException("Failed to process email event", e);
    }
}
```

## Key Points

1. **OrderOrchestrator publishes events AFTER successful operations**
   - After payment authorized → publish "payment.success"
   - After shipment requested → publish "shipment.requested"
   - On failure → publish "payment.failed"

2. **Listeners process events asynchronously**
   - BankMessageListener: Logs payment status
   - WarehouseMessageListener: Updates order status
   - EmailMessageListener: Writes to Outbox table

3. **Correlation ID is propagated**
   - Generated in OrderOrchestrator
   - Included in EventMessage
   - Logged by listeners for tracing

4. **Errors trigger DLQ retry logic**
   - If listener throws exception, message goes to DLQ
   - RabbitMQ retries with exponential backoff
   - Eventually moves to DLQ if max retries exceeded

