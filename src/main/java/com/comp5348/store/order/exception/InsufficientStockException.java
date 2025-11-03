package com.comp5348.store.order.exception;

/**
 * Exception thrown when there is insufficient stock to fulfill an order.
 */
public class InsufficientStockException extends RuntimeException {

    private final String itemId;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(String itemId, int requestedQuantity, int availableQuantity) {
        super(String.format(
            "Insufficient stock for item %s: requested %d but only %d available",
            itemId, requestedQuantity, availableQuantity));
        this.itemId = itemId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public String getItemId() {
        return itemId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}

