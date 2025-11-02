package com.comp5348.warehouse.model;

public class Warehouse {
    private Long warehouseId;
    private String location;

    public Warehouse(Long warehouseId, String location) {
        this.warehouseId = warehouseId;
        this.location = location;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getLocation() {
        return location;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}

