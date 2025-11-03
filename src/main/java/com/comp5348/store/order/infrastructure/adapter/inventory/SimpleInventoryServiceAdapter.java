package com.comp5348.store.order.infrastructure.adapter.inventory;

import com.comp5348.store.order.application.port.InventoryServicePort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Minimal inventory adapter that pretends every reservation succeeds against a single
 * warehouse location. This keeps the application layer decoupled while allowing the
 * orchestrator to be exercised end-to-end during local development or tests.
 */
@Component
public class SimpleInventoryServiceAdapter implements InventoryServicePort {

    private static final Logger log = LoggerFactory.getLogger(SimpleInventoryServiceAdapter.class);
    private static final String DEFAULT_LOCATION = "WH-LOCAL";

    private final Map<UUID, List<Allocation>> reservations = new ConcurrentHashMap<>();

    @Override
    public ReserveResult reserve(UUID orderId, String itemId, int quantity) {
        Allocation allocation = new Allocation(DEFAULT_LOCATION, quantity);
        reservations.put(orderId, List.of(allocation));
        log.debug("Reserved {} units of {} for order {}", quantity, itemId, orderId);
        return ReserveResult.success(List.of(allocation));
    }

    @Override
    public void release(UUID orderId) {
        reservations.remove(orderId);
        log.debug("Released reservation for order {}", orderId);
    }

    @Override
    public void deduct(UUID orderId) {
        reservations.remove(orderId);
        log.debug("Deducted inventory for order {}", orderId);
    }
}
