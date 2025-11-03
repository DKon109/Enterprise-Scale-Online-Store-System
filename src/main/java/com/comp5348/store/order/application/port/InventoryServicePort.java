package com.comp5348.store.order.application.port;

import java.util.List;
import java.util.UUID;

public interface InventoryServicePort {

    ReserveResult reserve(UUID orderId, String itemId, int quantity);

    void release(UUID orderId);

    void deduct(UUID orderId);

    final class ReserveResult {
        private final boolean success;
        private final List<Allocation> allocations;
        private final String reason;

        private ReserveResult(boolean success, List<Allocation> allocations, String reason) {
            this.success = success;
            this.allocations = allocations;
            this.reason = reason;
        }

        public static ReserveResult success(List<Allocation> allocations) {
            return new ReserveResult(true, allocations, null);
        }

        public static ReserveResult failure(String reason) {
            return new ReserveResult(false, List.of(), reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public List<Allocation> allocations() {
            return allocations;
        }

        public String reason() {
            return reason;
        }
    }

    record Allocation(String locationId, int quantity) { }
}
