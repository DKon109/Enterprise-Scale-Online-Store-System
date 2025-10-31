package com.comp5348.store.inventory.repository;
import com.comp5348.store.inventory.model.Inventory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory repository for now.
 * Maps (warehouseId, productId) --> Inventory
 */

public class InventoryRepository {

    //key = warehouseId + ":" + productId
    private  final Map<String, Inventory> inventoryMap = new ConcurrentHashMap<>();

    private String key(Long warehouseId, Long productId) {
        return warehouseId + "-" + productId;
    }

    public Optional<Inventory>  findByWarehouseAndProduct(Long warehouseId, Long productId) {
        return Optional.ofNullable(inventoryMap.get(key(warehouseId, productId)));
    }

    public List<Inventory> findAllByProduct(Long productId){
        List<Inventory> list = new ArrayList<>();
        for (Inventory inv : inventoryMap.values()) {
            if (inv.getProductId().equals(productId)) {
                list.add(inv);
            }
        }
        return list;
    }

    public void save(Inventory inv) {
        inventoryMap.put(key(inv.getWarehouseId(), inv.getProductId()), inv);
    }

    public Collection<Inventory> findAll(){
        return inventoryMap.values();
    }

    //helper: preload data for demo
    public void preload(Inventory... items){
        for (Inventory i : items){
            save(i);
        }
    }
}
