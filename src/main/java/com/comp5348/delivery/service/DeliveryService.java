package com.comp5348.delivery.service;

import com.comp5348.delivery.model.Delivery;
import com.comp5348.delivery.repository.DeliveryRepository;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;



/**
 * Business Logic for managing delivery lifecycle
 * Called from Order and Fulfilment workflows.
 */

@Service
public class DeliveryService {
    private  final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final Random random = new Random();

    public DeliveryService(DeliveryRepository deliveryRepository, OrderRepository orderRepository) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
    }


    /**
     * Create a new delivery
     * Basically it is called after warehouse and inventory confirmation
     * After ~5 seconds, automatically mark as DISPATCHED, with ~5% chance of being lost (CANCELLED).
     */
    @Transactional
    public Delivery createDelivery(UUID orderId, Long warehouseId, String address, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        Delivery delivery = new Delivery(order, warehouseId, address, trackingNumber);
        Delivery saved = deliveryRepository.save(delivery);

        //update the status automatically after 5 sec (5% failure)

        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                Optional<Delivery> opt = deliveryRepository.findById(saved.getId());
                if (opt.isPresent()) {
                    Delivery d = opt.get();

                    // 5% failure
                    if (random.nextDouble() < 0.05) {
                        d.cancel(); //fail
                    } else {
                        d.markDispatched(); // dispatched from the stock
                    }
                    deliveryRepository.save(d);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 5, TimeUnit.SECONDS);

        return saved;
    }



    /** retrieve the all deliveries associated with the same order
     */
    @Transactional(readOnly = true)
    public List<Delivery> getDeliveriesByOrderId(UUID orderId) {
        return deliveryRepository.findByOrder_OrderId(orderId);
    }

    /**
     * Retrieve the delivery by tracking number
     */
    @Transactional(readOnly = true)
    public Optional<Delivery> getByTrackingNumber(String trackingNumber) {
        return deliveryRepository.findByTrackingNumber(trackingNumber);
    }

    /**
     * Update the delivery status to DISPATCHED and record timestamp
     */
    @Transactional
    public boolean markDispatched(Long deliveryId) {
        Optional<Delivery> opt = deliveryRepository.findById(deliveryId);
        if (opt.isEmpty()) {
            return false;
        }
        Delivery delivery = opt.get();
        delivery.markDispatched();
        deliveryRepository.save(delivery);
        return true;
    }

    /**Update the delivery status to DELIVERED
     */
    @Transactional
    public boolean markDelivered(Long deliveryId) {
        Optional<Delivery> opt = deliveryRepository.findById(deliveryId);
        if (opt.isEmpty()) {
            return false;
        }
        Delivery delivery = opt.get();
        delivery.markDelivered();
        deliveryRepository.save(delivery);
        return true;
    }

    /**
     * Cancel an existing delivery
     */
    @Transactional
    public boolean cancelDelivery(Long deliveryId) {
        Optional<Delivery> opt = deliveryRepository.findById(deliveryId);
        if (opt.isEmpty()) {
            return false;
        }
        Delivery delivery = opt.get();
        delivery.cancel();
        deliveryRepository.save(delivery);
        return true;
    }

    /** Lightweight check to confirm if a delivery exists for a given order
     */
    @Transactional(readOnly = true)
    public boolean hasDeliveryForOrder(UUID orderId) {
        return deliveryRepository.existsByOrder_OrderId(orderId);
    }

    @Transactional
    public boolean markPickedUp(String trackingNumber) {
        return updateStatusByTracking(trackingNumber, Delivery::markDispatched);
    }

    @Transactional
    public boolean markInTransit(String trackingNumber) {
        return updateStatusByTracking(trackingNumber, Delivery::markInTransit);
    }

    @Transactional
    public boolean markDelivered(String trackingNumber) {
        return updateStatusByTracking(trackingNumber, Delivery::markDelivered);
    }

    private boolean updateStatusByTracking(String trackingNumber, Consumer<Delivery> updater) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return false;
        }
        Optional<Delivery> opt = deliveryRepository.findByTrackingNumber(trackingNumber);
        if (opt.isEmpty()) {
            return false;
        }
        Delivery delivery = opt.get();
        updater.accept(delivery);
        deliveryRepository.save(delivery);
        return true;
    }

}
