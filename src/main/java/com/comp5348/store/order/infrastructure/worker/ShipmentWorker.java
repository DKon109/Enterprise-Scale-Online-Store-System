package com.comp5348.store.order.infrastructure.worker;

import com.comp5348.store.order.application.service.OrderOrchestrator;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.repository.OrderRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically processes PAID orders and requests shipment.
 * 
 * COMPLIANCE: §50-54 - Order Cancellation (Before Delivery Request Sent)
 * 
 * This worker creates a window between PAID and SHIPMENT_REQUESTED status,
 * allowing customers to cancel orders before the delivery request is sent to DeliveryCo.
 * 
 * The worker runs every N seconds (configurable) and processes all orders in PAID status.
 */
@Component
public class ShipmentWorker {

    private static final Logger log = LoggerFactory.getLogger(ShipmentWorker.class);

    private final OrderRepository orderRepository;
    private final OrderOrchestrator orderOrchestrator;
    private final long delayMillis;

    public ShipmentWorker(
            OrderRepository orderRepository,
            OrderOrchestrator orderOrchestrator,
            @Value("${shipment.worker.interval:10000}") long delayMillis) {
        this.orderRepository = orderRepository;
        this.orderOrchestrator = orderOrchestrator;
        this.delayMillis = delayMillis;
    }

    /**
     * Process all PAID orders and request shipment.
     * 
     * This runs periodically to create a cancellation window.
     * Default interval: 5 minutes (configurable via shipment.worker.interval)
     */
    @Scheduled(fixedDelayString = "${shipment.worker.interval:10000*10}") // 5 minutes
    public void processShipments() {
        List<Order> paidOrders = orderRepository.findByStatus(Order.Status.PAID.name());
        
        if (paidOrders.isEmpty()) {
            return;
        }

        log.info("Processing {} PAID order(s) for shipment", paidOrders.size());
        
        for (Order order : paidOrders) {
            try {
                log.info("Requesting shipment for order {} (correlation: {})", 
                    order.getOrderId(), order.getCorrelationId());
                
                orderOrchestrator.processShipment(order.getOrderId(), order.getCorrelationId());
                
                log.info("Shipment request completed for order {}", order.getOrderId());
            } catch (Exception ex) {
                log.error("Failed to process shipment for order {}: {}", 
                    order.getOrderId(), ex.getMessage(), ex);
                // Continue processing other orders even if one fails
            }
        }
    }
}

