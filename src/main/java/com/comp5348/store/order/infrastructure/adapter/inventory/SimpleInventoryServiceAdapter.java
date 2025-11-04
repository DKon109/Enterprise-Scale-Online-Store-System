package com.comp5348.store.order.infrastructure.adapter.inventory;

import com.comp5348.store.order.application.port.InventoryServicePort;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP-based inventory adapter that calls the Inventory service API.
 *
 * Integrates with Inventory service at:
 * - POST /inventory/reserve (reserve stock)
 * - POST /inventory/commit (commit reservation)
 * - POST /inventory/release (release reservation)
 */
@Component
public class SimpleInventoryServiceAdapter implements InventoryServicePort {

    private static final Logger log = LoggerFactory.getLogger(SimpleInventoryServiceAdapter.class);

    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;

    public SimpleInventoryServiceAdapter(RestTemplate restTemplate, @Value("${inventory.service.url:http://localhost:8081}") String inventoryServiceUrl) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    @Override
    public ReserveResult reserve(UUID orderId, String itemId, int quantity) {
        try {
            // Call Inventory service to reserve stock
            String url = inventoryServiceUrl + "/inventory/reserve?productId=" + itemId + "&qty=" + quantity;

            AllocationDto[] allocations = restTemplate.postForObject(url, null, AllocationDto[].class);

            if (allocations != null && allocations.length > 0) {
                List<Allocation> allocationList = List.of(allocations).stream()
                        .map(dto -> new Allocation(dto.locationId, dto.quantity))
                        .toList();
                log.debug("Reserved {} units of {} for order {}", quantity, itemId, orderId);
                return ReserveResult.success(allocationList);
            } else {
                log.warn("Inventory reservation failed for order {}: no allocations returned", orderId);
                return ReserveResult.failure("INSUFFICIENT_STOCK");
            }
        } catch (RestClientException e) {
            log.error("Inventory service error during reservation for order {}: {}", orderId, e.getMessage());
            return ReserveResult.failure("INVENTORY_SERVICE_ERROR");
        }
    }

    @Override
    public void release(UUID orderId) {
        try {
            // Call Inventory service to release reservation
            String url = inventoryServiceUrl + "/inventory/release";
            restTemplate.postForObject(url, null, Void.class);
            log.debug("Released reservation for order {}", orderId);
        } catch (RestClientException e) {
            log.error("Inventory service error during release for order {}: {}", orderId, e.getMessage());
        }
    }

    @Override
    public void deduct(UUID orderId) {
        try {
            // Call Inventory service to commit reservation
            String url = inventoryServiceUrl + "/inventory/commit";
            restTemplate.postForObject(url, null, Void.class);
            log.debug("Deducted inventory for order {}", orderId);
        } catch (RestClientException e) {
            log.error("Inventory service error during deduct for order {}: {}", orderId, e.getMessage());
        }
    }

    /**
     * DTO for Inventory service allocation response
     */
    public static class AllocationDto {
        public String locationId;
        public int quantity;
    }
}
