package com.comp5348.store.order.infrastructure.adapter.shipping;

import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shipping adapter that generates a synthetic tracking ID for every shipment request and stores
 * it in-memory so subsequent operations (e.g. status polling) could be added later if needed.
 */
@Component
public class SimpleShippingServiceAdapter implements ShippingServicePort {

    private static final Logger log = LoggerFactory.getLogger(SimpleShippingServiceAdapter.class);

    private final ConcurrentMap<UUID, String> shipments = new ConcurrentHashMap<>();

    @Override
    public ShipmentResult request(UUID orderId, List<InventoryServicePort.Allocation> allocations) {
        String trackingId = "TRACK-" + orderId.toString().substring(0, 8).toUpperCase();
        shipments.put(orderId, trackingId);
        log.debug("Created shipment {} for order {} with allocations {}", trackingId, orderId, allocations);
        return new ShipmentResult(true, trackingId);
    }

    public String lookupTrackingId(UUID orderId) {
        return shipments.get(orderId);
    }
}
