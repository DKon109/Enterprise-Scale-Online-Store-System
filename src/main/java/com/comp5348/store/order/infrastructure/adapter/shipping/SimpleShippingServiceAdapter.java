package com.comp5348.store.order.infrastructure.adapter.shipping;

import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * HTTP-based shipping adapter that calls the Delivery service API.
 *
 * Integrates with Delivery service at:
 * - POST /deliveries (create delivery/shipment)
 *
 * Supports X-Simulate-Delivery-Failure header for testing failure scenarios.
 */
@Component
public class SimpleShippingServiceAdapter implements ShippingServicePort {

    private static final Logger log = LoggerFactory.getLogger(SimpleShippingServiceAdapter.class);

    private final RestTemplate restTemplate;
    private final String deliveryServiceUrl;

    public SimpleShippingServiceAdapter(RestTemplate restTemplate, @Value("${delivery.service.url:http://localhost:8081}") String deliveryServiceUrl) {
        this.restTemplate = restTemplate;
        this.deliveryServiceUrl = deliveryServiceUrl;
    }

    @Override
    public ShipmentResult request(UUID orderId, List<InventoryServicePort.Allocation> allocations) {
        // Check for delivery failure simulation header
        if (isDeliveryFailureSimulated()) {
            log.debug("Delivery failure simulated for order {} (X-Simulate-Delivery-Failure header present)", orderId);
            return new ShipmentResult(false, null);
        }

        try {
            // Call Delivery service to create a delivery
            String url = deliveryServiceUrl + "/deliveries";

            // Use first allocation's location as warehouse ID (simplified)
            Long warehouseId = 1L;  // Default warehouse
            String address = "Default Address";  // Would come from order in real scenario
            String trackingNumber = "TRACK-" + orderId.toString().substring(0, 8).toUpperCase();

            CreateDeliveryRequest request = new CreateDeliveryRequest(orderId, warehouseId, address, trackingNumber);

            DeliveryResponse response = restTemplate.postForObject(url, request, DeliveryResponse.class);

            if (response != null && response.trackingNumber != null) {
                log.debug("Created shipment {} for order {} with allocations {}", response.trackingNumber, orderId, allocations);
                return new ShipmentResult(true, response.trackingNumber);
            } else {
                log.warn("Delivery creation failed for order {}: {}", orderId, response);
                return new ShipmentResult(false, null);
            }
        } catch (RestClientException e) {
            log.error("Delivery service error during shipment request for order {}: {}", orderId, e.getMessage());
            return new ShipmentResult(false, null);
        }
    }

    /**
     * Check if the X-Simulate-Delivery-Failure header is present in the current request.
     * This allows testing delivery failure scenarios.
     */
    private boolean isDeliveryFailureSimulated() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String header = request.getHeader("X-Simulate-Delivery-Failure");
                return "true".equalsIgnoreCase(header);
            }
        } catch (Exception e) {
            log.debug("Could not check for delivery failure simulation header", e);
        }
        return false;
    }

    /**
     * DTO for Delivery service creation request
     */
    public static class CreateDeliveryRequest {
        public UUID orderId;
        public Long warehouseId;
        public String address;
        public String trackingNumber;

        public CreateDeliveryRequest(UUID orderId, Long warehouseId, String address, String trackingNumber) {
            this.orderId = orderId;
            this.warehouseId = warehouseId;
            this.address = address;
            this.trackingNumber = trackingNumber;
        }
    }

    /**
     * DTO for Delivery service response
     */
    public static class DeliveryResponse {
        public Long id;
        public UUID orderId;
        public String trackingNumber;
        public String status;
    }
}
