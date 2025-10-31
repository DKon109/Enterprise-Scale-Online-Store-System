package com.comp5348.store.inventory.model;

public class Inventory {
    private Long warehouseId;
    private Long productId;
    private int quantityAvailable;
    private int quantityReserved;

    public Inventory(Long warehouseId, Long productId, int quantityAvailable, int quantityReserved) {
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.quantityAvailable = quantityAvailable;
        this.quantityReserved = quantityReserved;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantityAvailable() {
        return quantityAvailable;
    }

    public int getQuantityReserved() {
        return quantityReserved;
    }

    public void setQuantityAvailable(int quantityAvailable) {
        this.quantityAvailable = quantityAvailable;
    }

    public void setQuantityReserved(int quantityReserved) {
        this.quantityReserved = quantityReserved;
    }

    //domain helpers

    //**
    // reserve qty items: reduce quantityAvailable and increase quantityReserved
    // use the boolean to check if it goes through*/
    public boolean reserve(int qty) {
        if (qty <= 0) return false;
        if (quantityAvailable < qty){
            return false;
        }
        quantityAvailable -= qty;
        quantityReserved += qty;
        return true;
    }

    /** release the reserved quantity: reduce the quantityReserved and increase quantityAvailable */
    public boolean release(int qty) {
        if (qty <= 0) return false;
        if (quantityReserved < qty){
            return false;
        }
        quantityReserved -= qty;
        quantityAvailable += qty;
        return true;
    }

    /**
     * commit the reserved stock. Since it will proceed to delivery, it will reduce "reserved".
     * don't put it back to "available".
     */
    public boolean commitReserved(int qty) {
        if (qty <= 0) return false;
        if (quantityReserved < qty){
            return false;
        }
        quantityReserved -= qty;
        //available is already reduced
        return true;
    }
}
