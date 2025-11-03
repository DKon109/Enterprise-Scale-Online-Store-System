package com.comp5348.warehouse.controller;

import com.comp5348.warehouse.model.Warehouse;
import com.comp5348.warehouse.service.WarehouseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

@RestController
@RequestMapping("/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;
    public WarehouseController(WarehouseService warehouseService){
        this.warehouseService = warehouseService;
    }

    //GET /warehouses
    @GetMapping
    public Collection<Warehouse> listWarehouses(){
        return warehouseService.listWarehouses();
    }

    //GET /warehouses/ {id}
    @GetMapping("/{id}")
    public ResponseEntity<Warehouse> getWarehouse(@PathVariable Long id){
        Optional<Warehouse> wh = warehouseService.getWarehouse(id);
        return wh.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    //POST/ warehouses/ add?id=1&location=Sydney
    @PostMapping("/add")
    public ResponseEntity<Warehouse> addWarehouse(@RequestParam String location){
        Warehouse created = warehouseService.addWarehouse(location);
        // Include the URI of the newly created resource in the Location header
        return ResponseEntity
                .created(URI.create("/warehouses/" + created.getId()))
                .body(created);
    }
}
