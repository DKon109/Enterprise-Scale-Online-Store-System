package com.comp5348.store.order.application.port;

import java.util.List;
import java.util.UUID;

public interface ShippingServicePort {

    ShipmentResult request(UUID orderId, List<InventoryServicePort.Allocation> allocations);

    final class ShipmentResult {
        private final boolean accepted;
        private final String trackingId;

        public ShipmentResult(boolean accepted, String trackingId) {
            this.accepted = accepted;
            this.trackingId = trackingId;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String trackingId() {
            return trackingId;
        }
    }
}
