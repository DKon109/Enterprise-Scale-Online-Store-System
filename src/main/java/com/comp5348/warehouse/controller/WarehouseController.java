package com.comp5348.warehouse.controller;

import com.comp5348.warehouse.model.Warehouse;
import com.comp5348.warehouse.service.WarehouseService;

import java.util.Collection;
import java.util.Optional;

public class WarehouseController {

    private final WarehouseService warehouseService;
    public WarehouseController(WarehouseService warservice){
        warehouseService = warservice;
    }

    //GET /warehouses
    public Collection<Warehouse> listWarehouses(){
        return warehouseService.listWarehouses();
    }

    //GET /warehouses/ {id}
    public Optional<Warehouse> getWarehouse(Long id){
        return warehouseService.getWarehouse(id);
    }

    //POST/ warehouses/ add?id=1&location=Sydney
    public Warehouse addWarehouse(Long id, String location){
        return warehouseService.addWarehouse(id, location);
    }
}
