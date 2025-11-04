package com.comp5348.store.order.infrastructure.adapter.inventory;

import com.comp5348.store.order.application.port.InventoryServicePort;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private final ConcurrentMap<UUID, List<AllocationDto>> reservationCache = new ConcurrentHashMap<>();

    public SimpleInventoryServiceAdapter(RestTemplate restTemplate, @Value("${inventory.service.url:http://localhost:8081}") String inventoryServiceUrl) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    @Override
    public ReserveResult reserve(UUID orderId, String itemId, int quantity) {
        try {
            // Call Inventory service to reserve stock
            String url = inventoryServiceUrl + "/inventory/reserve?productId=" + itemId + "&qty=" + quantity;

            AllocationDto[] response = restTemplate.postForObject(url, null, AllocationDto[].class);
            List<AllocationDto> allocations = normaliseAllocations(response);

            if (allocations.isEmpty()) {
                log.warn("Inventory reservation failed for order {}: no allocations returned", orderId);
                return ReserveResult.failure("INSUFFICIENT_STOCK");
            }

            reservationCache.put(orderId, allocations);
            List<Allocation> allocationList = allocations.stream()
                    .map(AllocationDto::toPortAllocation)
                    .toList();
            log.debug("Reserved {} units of {} for order {} with allocations {}", quantity, itemId, orderId, allocationList);
            return ReserveResult.success(allocationList);
        } catch (RestClientException e) {
            log.error("Inventory service error during reservation for order {}: {}", orderId, e.getMessage());
            return ReserveResult.failure("INVENTORY_SERVICE_ERROR");
        }
    }

    @Override
    public void release(UUID orderId) {
        processReservation(orderId, "/inventory/release", "release");
    }

    @Override
    public void deduct(UUID orderId) {
        processReservation(orderId, "/inventory/commit", "commit");
    }

    @Override
    public List<Allocation> allocations(UUID orderId) {
        List<AllocationDto> cached = reservationCache.get(orderId);
        if (cached == null || cached.isEmpty()) {
            return List.of();
        }
        return cached.stream()
                .map(AllocationDto::toPortAllocation)
                .toList();
    }

    private void processReservation(UUID orderId, String path, String action) {
        List<AllocationDto> allocations = reservationCache.get(orderId);
        if (allocations == null || allocations.isEmpty()) {
            log.warn("No cached inventory allocations for order {} to {}", orderId, action);
            return;
        }

        try {
            String url = inventoryServiceUrl + path;
            AllocationDto[] payload = allocations.toArray(new AllocationDto[0]);
            restTemplate.postForObject(url, payload, Void.class);
            reservationCache.remove(orderId, allocations);
            log.debug("Completed inventory {} for order {}", action, orderId);
        } catch (RestClientException e) {
            log.error("Inventory service error during {} for order {}: {}", action, orderId, e.getMessage());
        }
    }

    private List<AllocationDto> normaliseAllocations(AllocationDto[] response) {
        if (response == null || response.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(response)
                .filter(dto -> dto != null && dto.warehouseId != null && dto.productId != null && dto.quantity > 0)
                .map(AllocationDto::copyOf)
                .toList();
    }

    /**
     * DTO for Inventory service allocation response and request.
     */
    public static class AllocationDto {
        public Long warehouseId;
        public Long productId;
        public int quantity;

        public AllocationDto() {
            // For JSON deserialisation
        }

        public AllocationDto(Long warehouseId, Long productId, int quantity) {
            this.warehouseId = warehouseId;
            this.productId = productId;
            this.quantity = quantity;
        }

        InventoryServicePort.Allocation toPortAllocation() {
            return new InventoryServicePort.Allocation(warehouseId, productId, quantity);
        }

        static AllocationDto copyOf(AllocationDto source) {
            return new AllocationDto(source.warehouseId, source.productId, source.quantity);
        }
    }
}
