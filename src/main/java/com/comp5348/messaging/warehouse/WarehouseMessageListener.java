package com.comp5348.messaging.warehouse;

import com.comp5348.messaging.events.EventMessage;
import com.comp5348.store.order.application.service.OrderOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens to warehouse_queue and processes warehouse/delivery-related events.
 *
 * This listener reacts to events published by OrderOrchestrator or DeliveryWebhookController.
 * It updates order status based on warehouse/delivery events.
 *
 * Event Sources:
 * 1. OrderOrchestrator publishes "shipment.requested" after requesting shipment
 * 2. DeliveryWebhookController publishes events from DeliveryCo webhooks
 *
 * Compliance: §79-80 (Reliable asynchronous messaging)
 */
@Component
public class WarehouseMessageListener {

    private static final Logger log = LoggerFactory.getLogger(WarehouseMessageListener.class);
    private final OrderOrchestrator orderOrchestrator;

    public WarehouseMessageListener(OrderOrchestrator orderOrchestrator) {
        this.orderOrchestrator = orderOrchestrator;
    }

    /**
     * Process warehouse/delivery events from warehouse_queue.
     *
     * Event Flow:
     * 1. OrderOrchestrator publishes "shipment.requested" to warehouse_queue
     * 2. DeliveryWebhookController publishes delivery status updates
     * 3. This listener receives events asynchronously
     * 4. This listener updates order status
     *
     * @param event The warehouse/delivery event from RabbitMQ
     */
    @RabbitListener(queues = "warehouse_queue")
    public void onMessage(EventMessage event) {
        try {
            String correlationId = event.getCorrelationId() != null ? event.getCorrelationId() : "N/A";

            switch (event.getType()) {
                case "item.preparing" -> handleItemPreparing(event, correlationId);
                case "item.shipped" -> handleItemShipped(event, correlationId);
                case "item.delivered" -> handleItemDelivered(event, correlationId);
                case "shipment.requested" -> handleShipmentRequested(event, correlationId);
                default -> log.debug("[Warehouse] Unknown event type: {} for order {}", event.getType(), event.getOrderId());
            }
        } catch (Exception e) {
            log.error("[Warehouse] Error processing event: {} for order {}. Correlation ID: {}. Error: {}",
                event.getType(), event.getOrderId(), event.getCorrelationId(), e.getMessage(), e);
            // Exception will trigger DLQ retry logic
            throw new RuntimeException("Failed to process warehouse event", e);
        }
    }

    /**
     * Handle item preparing event.
     * Warehouse is preparing the item for shipment.
     */
    private void handleItemPreparing(EventMessage event, String correlationId) {
        log.info("[Warehouse] 📦 Item PREPARING for order {} | Correlation: {}",
            event.getOrderId(), correlationId);

        // Optional: Update order status to PREPARING
        // Example: orderOrchestrator.markPreparing(event.getOrderId());
    }

    /**
     * Handle item shipped event.
     * Item has been shipped and is on the way.
     */
    private void handleItemShipped(EventMessage event, String correlationId) {
        log.info("[Warehouse] 🚚 Item SHIPPED for order {} | Correlation: {}",
            event.getOrderId(), correlationId);

        // Optional: Update order status to SHIPPED
        // Example: orderOrchestrator.markShipped(event.getOrderId());
    }

    /**
     * Handle item delivered event.
     * Item has been delivered to customer.
     */
    private void handleItemDelivered(EventMessage event, String correlationId) {
        log.info("[Warehouse] ✅ Item DELIVERED for order {} | Correlation: {}",
            event.getOrderId(), correlationId);

        // Update order status to DELIVERED
        try {
            orderOrchestrator.markDelivered(event.getOrderId());
            log.info("[Warehouse] Order {} marked as DELIVERED", event.getOrderId());
        } catch (Exception e) {
            log.error("[Warehouse] Failed to mark order {} as delivered: {}", event.getOrderId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Handle shipment requested event.
     * OrderOrchestrator has requested shipment from warehouse.
     */
    private void handleShipmentRequested(EventMessage event, String correlationId) {
        log.info("[Warehouse] 📋 Shipment REQUESTED for order {} | Correlation: {}",
            event.getOrderId(), correlationId);

        // Optional: Trigger warehouse fulfillment process
        // Example: warehouseService.fulfillOrder(event.getOrderId());
    }
}
