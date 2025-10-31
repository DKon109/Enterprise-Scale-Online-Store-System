package com.comp5348.store.inventory.service;

import com.comp5348.store.inventory.model.Inventory;
import com.comp5348.store.inventory.repository.InventoryRepository;

import java.util.*;

/**
 * Manage the stock reservation, commit, and cancel.
 * This class is called in the flow of Order and Fulfillment.
 */
public class InventoryService {
    private final InventoryRepository invRep;

    public static class Allocation{
        public final Long warehouseId;
        public final Long productId;
        public final int quantity;

        public Allocation(Long warehouseId, Long productId, int quantity) {
            this.warehouseId = warehouseId;
            this.productId = productId;
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
     */

    public Optional<List<Allocation>> reserveProduct(Long productId, int requestedQty){
        if (requestedQty <= 0){
            return Optional.empty();
        }

        List<Inventory> candidates = invRep.findAllByProduct(productId);

        int remaining = requestedQty;
        List<Inventory> touched = new ArrayList<>();
        List<Allocation> plan = new ArrayList<>();

        for (Inventory inv: candidates) {
            if (remaining <= 0) break;

            int canTake = Math.min(inv.getQuantityAvailable(), remaining);
            if (canTake > 0) {
                boolean ok = inv.reserve(canTake);
                if (!ok){
                    //if it fails, roll back what's already reserved
                    rollbackReservation(touched, plan);
                    return Optional.empty();
                }
                touched.add(inv);
                plan.add(new Allocation(inv.getWarehouseId(), inv.getProductId(), canTake));
                remaining -= canTake;
            }
        }

        if (remaining > 0){
            //if there's not enough stock, rollback
            rollbackReservation(touched, plan);
            return Optional.empty();
        }

        //persist the updated inventories
        for (Inventory inv: touched) {
            invRep.save(inv);
        }

        return Optional.of(plan);
    }

    private void rollbackReservation(List<Inventory> touched, List<Allocation> plan) {
        for (int i = 0; i< touched.size(); i++){
            Inventory inv = touched.get(i);
            Allocation alc = plan.get(i);
            inv.release(alc.quantity); //undo
        }
    }

    /**commit the reservation.
     * After payment success and attanged the delivery, it will finalize.
     * We assume that we use same allocation list from reserveProduct.
     */
    public boolean commitReservation(List<Allocation> allocations){
        for (Allocation a :  allocations){
            Optional<Inventory> opt = invRep.findByWarehouseAndProduct(a.warehouseId, a.productId);
            if (opt.isEmpty()) return false;
            Inventory inv = opt.get();
            boolean ok = inv.commitReserved(a.quantity);
            if (!ok) return false;
            invRep.save(inv);
        }
        return true;
    }

    /** Release the reservation stock in case it is refund or canceled
     * Put the quantity of reservation back to available stock
     */
    public boolean releaseReservation(List<Allocation> allocations){
        for (Allocation a :  allocations){
            Optional<Inventory> opt = invRep.findByWarehouseAndProduct(a.warehouseId, a.productId);
            if (opt.isEmpty()) return false;
            Inventory inv = opt.get();
            boolean ok = inv.release(a.quantity);
            if (!ok) return false;
            invRep.save(inv);
        }
        return true;
    }

    /** Read-only helper for debugging / demo*/
    public Collection<Inventory> viewAllInventory(){
        return invRep.findAll();
    }
}
