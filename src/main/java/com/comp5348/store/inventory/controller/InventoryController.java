package com.comp5348.store.inventory.controller;

import com.comp5348.store.inventory.model.Inventory;
import com.comp5348.store.inventory.service.InventoryService;
import com.comp5348.store.inventory.service.InventoryService.Allocation;

import java.util.*;

public class InventoryController {

    private final InventoryService inventoryService;
    public InventoryController(InventoryService invSer) {
        this.inventoryService = invSer;
    }

    //GET / inventory
    public Collection<Inventory> listAllInventory() {
        return inventoryService.viewAllInventory();
    }

    // POST /inventory/reserve?productId={productId}&qty={qty}
    public Optional<List<Allocation>> reserve(Long productId, int qty){
        return inventoryService.reserveProduct(productId, qty);
    }

    //POST/ inventory/ commit (body: allocations)
    public boolean commit(List<Allocation> allocations){
        return inventoryService.commitReservation(allocations);
    }

    //POST / inventory/ release (body: allocations)
    public boolean release(List<Allocation> allocations){
        return inventoryService.releaseReservation(allocations);
    }
}

