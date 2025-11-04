package com.comp5348.store.inventory.service;

import com.comp5348.store.inventory.model.Inventory;
import com.comp5348.store.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Manage the stock reservation, commit, and cancel.
 * This class is called in the flow of Order and Fulfillment.
 */
@Service
public class InventoryService {
    private final InventoryRepository invRep;

    public static class Allocation{
        public final Long warehouseId;
        public final Long productId;
        public final int quantity;

        public Allocation(Long warehouseId, Long productId, int quantity) {
            this.warehouseId = warehouseId;
            this.productId = productId;
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
            this.quantity = quantity;
        }
    }

    public InventoryService(InventoryRepository invRep) {
        this.invRep = invRep;
    }

    /** It will try to reserve the stock for given item and quantity.
     *  If reservation succeed, return the list of allocations. (warehouse--> qty)
     *  If it failed, return empty Optional.
     *
     *  The flow of Algorithm:
     *  1. Get all inventories for that product.
     *  2. sort or iterate in order to make an allocation plan
     *  3. Apply reserve() method for each chosen Inventory
     *  4. If fully allocated, persist with saveAll, else revert in-memory changes and fail
     */

    @Transactional
    public Optional<List<Allocation>> reserveProduct(Long productId, int requestedQty) {
        if (productId == null || requestedQty <= 0) {
            return Optional.empty();
        }

        List<Inventory> candidates = invRep.findByProductId(productId);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        //Use warehouse in descending order of available stock
        candidates.sort(Comparator.comparingInt(Inventory::getQuantityAvailable).reversed());

        int remaining = requestedQty;
        List<Inventory> touched = new ArrayList<>();
        List<Allocation> plan = new ArrayList<>();

        for (Inventory inv : candidates) {
            if (remaining <= 0) break;

            int canTake = Math.min(inv.getQuantityAvailable(), remaining);
            if (canTake > 0) {
                boolean ok = inv.reserve(canTake);
                if (!ok) {
                    //if it fails, roll back what's already reserved
                    rollbackReservation(touched, plan);
                    return Optional.empty();
                }
                touched.add(inv);
                plan.add(new Allocation(inv.getWarehouseId(), inv.getProductId(), canTake));
                remaining -= canTake;
            }
        }

        if (remaining > 0) {
            //if there's not enough stock, rollback
            rollbackReservation(touched, plan);
            return Optional.empty();
        }

        //If all allocations succeed, persist changes in batch
        invRep.saveAll(touched);
        return Optional.of(Collections.unmodifiableList(plan));
    }

    private void rollbackReservation(List<Inventory> touched, List<Allocation> plan) {
        for (int i = 0; i< touched.size(); i++){
            touched.get(i).release(plan.get(i).quantity);
        }
    }

    /**commit the reservation.
     * After payment success and attanged the delivery, it will finalize.
     * We assume that we use same allocation list from reserveProduct.
     */
    @Transactional
    public boolean commitReservation(List<Allocation> allocations){
        if (allocations == null || allocations.isEmpty()) {
            return false;
        }
        for (Allocation a :  allocations){
            Optional<Inventory> opt = invRep.findByWarehouseIdAndProductId(a.warehouseId, a.productId);
            if (opt.isEmpty()) return false;

            Inventory inv = opt.get();
            if (!inv.commitReserved(a.quantity)){
                return false;
            }
        }
        return true;
    }

    /** Release the reservation stock in case it is refund or canceled
     * Put the quantity of reservation back to available stock
     */
    @Transactional
    public boolean releaseReservation(List<Allocation> allocations){
        if (allocations == null || allocations.isEmpty()) {
            return false;
        }
        for (Allocation a :  allocations){
            Optional<Inventory> opt = invRep.findByWarehouseIdAndProductId(a.warehouseId, a.productId);
            if (opt.isEmpty()) return false;

            Inventory inv = opt.get();
            if (!inv.release(a.quantity)) return false;
            invRep.save(inv);
        }
        return true;
    }

    /** Read-only helper for debugging / demo*/
    public Collection<Inventory> viewAllInventory(){
        return invRep.findAll();
    }

    /** Add or update inventory stock for a product in a warehouse */
    @Transactional
    public Inventory addStock(Long warehouseId, Long productId, int quantity) {
        if (warehouseId == null || productId == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid warehouse, product, or quantity");
        }

        Optional<Inventory> existing = invRep.findByWarehouseIdAndProductId(warehouseId, productId);
        Inventory inv;
        if (existing.isPresent()) {
            inv = existing.get();
            inv.setQuantityAvailable(inv.getQuantityAvailable() + quantity);
        } else {
            inv = new Inventory(warehouseId, productId, quantity, 0);
        }
        return invRep.save(inv);
    }
}
