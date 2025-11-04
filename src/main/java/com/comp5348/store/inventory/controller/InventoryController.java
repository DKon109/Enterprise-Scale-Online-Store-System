package com.comp5348.store.inventory.controller;

import com.comp5348.store.inventory.model.Inventory;
import com.comp5348.store.inventory.service.InventoryService;
import com.comp5348.store.inventory.service.InventoryService.Allocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Inventory REST API
 *  - GET    /inventory
 *  - POST   /inventory/add?warehouseId=&productId=&qty=
 *  - POST   /inventory/reserve?productId=&qty=
 *  - POST   /inventory/commit
 *  - POST   /inventory/release
 */
@RestController
@RequestMapping("/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;
    public InventoryController(InventoryService invSer) {
        this.inventoryService = invSer;
    }

    //GET / inventory
    @GetMapping
    public ResponseEntity<Collection<Inventory>> listAllInventory() {
        return ResponseEntity.ok(inventoryService.viewAllInventory());
    }

    // POST /inventory/add?warehouseId={warehouseId}&productId={productId}&qty={qty}
    @PostMapping("/add")
    public ResponseEntity<Inventory> addStock(
            @RequestParam @NotNull @Min(1) Long warehouseId,
            @RequestParam @NotNull @Min(1) Long productId,
            @RequestParam @Min(1) int qty
    ) {
        Inventory inv = inventoryService.addStock(warehouseId, productId, qty);
        return ResponseEntity.status(HttpStatus.CREATED).body(inv);
    }

    // POST /inventory/reserve?productId={productId}&qty={qty}
    @PostMapping("/reserve")
    public ResponseEntity<List<AllocationDto>> reserve(
            @RequestParam @NotNull @Min(1) Long productId,
            @RequestParam @Min(1) int qty
    ) {
        Optional<List<Allocation>> result = inventoryService.reserveProduct(productId, qty);
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); //insufficient stock
        }
        //Service.Allocation -> convert to DTO and return
        List<AllocationDto> body = result.get().stream().map(AllocationDto::from).toList();
        return ResponseEntity.ok(body);
    }

    //POST/ inventory/ commit (body: allocations)
    @PostMapping("/commit")
    public ResponseEntity<Void> commit(@RequestBody @Valid List<AllocationDto> allocations){
        boolean ok = inventoryService.commitReservation(allocations.stream().map(AllocationDto::toAllocation).toList());
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    //POST / inventory/ release (body: allocations)
    @PostMapping("/release")
    public ResponseEntity<Void> release(@RequestBody @Valid List<AllocationDto> allocations){
        boolean ok = inventoryService.releaseReservation(allocations.stream().map(AllocationDto::toAllocation).toList());
        return ok ? ResponseEntity.noContent().build()
                  : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    //DTO
    public static class AllocationDto {
        @NotNull @Min(1) public Long warehouseId;
        @NotNull @Min(1) public Long productId;
        @Min(1) public int quantity;

        public AllocationDto() {} //for JSON deserialization

        public AllocationDto(Long warehouseId, Long productId, int quantity) {
            this.warehouseId = warehouseId;
            this.productId = productId;
            this.quantity = quantity;
        }

        static AllocationDto from(Allocation al) {
            return new AllocationDto(al.warehouseId, al.productId, al.quantity);
        }

        Allocation toAllocation() {
            return new Allocation(warehouseId, productId, quantity);
        }
    }
}

