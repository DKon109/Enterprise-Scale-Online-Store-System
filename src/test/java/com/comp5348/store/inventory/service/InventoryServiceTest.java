package com.comp5348.store.inventory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.comp5348.store.inventory.model.Inventory;
import com.comp5348.store.inventory.repository.InventoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void reserveProductAllocatesAcrossWarehouses() {
        Inventory invA = new Inventory(1L, 100L, 5, 0);
        Inventory invB = new Inventory(2L, 100L, 3, 0);

        when(inventoryRepository.findByProductId(100L))
                .thenReturn(new ArrayList<>(List.of(invA, invB)));

        Optional<List<InventoryService.Allocation>> result = inventoryService.reserveProduct(100L, 6);

        assertTrue(result.isPresent());
        List<InventoryService.Allocation> allocations = result.get();
        assertEquals(2, allocations.size());
        assertEquals(1L, allocations.get(0).warehouseId);
        assertEquals(5, allocations.get(0).quantity);
        assertEquals(2L, allocations.get(1).warehouseId);
        assertEquals(1, allocations.get(1).quantity);

        assertEquals(0, invA.getQuantityAvailable());
        assertEquals(5, invA.getQuantityReserved());
        assertEquals(2, invB.getQuantityAvailable());
        assertEquals(1, invB.getQuantityReserved());

        verify(inventoryRepository).saveAll(anyList());
    }

    @Test
    void reserveProductFailsWhenInsufficientStock() {
        Inventory inv = new Inventory(1L, 200L, 2, 0);
        when(inventoryRepository.findByProductId(200L)).thenReturn(new ArrayList<>(List.of(inv)));

        Optional<List<InventoryService.Allocation>> result = inventoryService.reserveProduct(200L, 5);

        assertTrue(result.isEmpty());
        assertEquals(2, inv.getQuantityAvailable());
        assertEquals(0, inv.getQuantityReserved());
        verify(inventoryRepository, never()).saveAll(anyList());
    }

    @Test
    void reserveProductWithInvalidInputReturnsEmpty() {
        Optional<List<InventoryService.Allocation>> result = inventoryService.reserveProduct(null, 5);
        assertTrue(result.isEmpty());
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void commitReservationSucceedsWhenAllInventoriesCommit() {
        InventoryService.Allocation allocation = new InventoryService.Allocation(1L, 100L, 2);
        Inventory inv = spy(new Inventory(1L, 100L, 5, 3));
        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 100L)).thenReturn(Optional.of(inv));
        when(inv.commitReserved(2)).thenReturn(true);

        boolean success = inventoryService.commitReservation(List.of(allocation));

        assertTrue(success);
        verify(inv).commitReserved(2);
    }

    @Test
    void commitReservationFailsWhenInventoryMissing() {
        InventoryService.Allocation allocation = new InventoryService.Allocation(1L, 100L, 2);
        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 100L)).thenReturn(Optional.empty());

        boolean success = inventoryService.commitReservation(List.of(allocation));

        assertFalse(success);
    }

    @Test
    void commitReservationFailsWhenCommitRejected() {
        InventoryService.Allocation allocation = new InventoryService.Allocation(1L, 100L, 2);
        Inventory inv = spy(new Inventory(1L, 100L, 5, 1));
        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 100L)).thenReturn(Optional.of(inv));
        when(inv.commitReserved(2)).thenReturn(false);

        boolean success = inventoryService.commitReservation(List.of(allocation));

        assertFalse(success);
    }

    @Test
    void releaseReservationRestoresStockAndPersists() {
        InventoryService.Allocation allocation = new InventoryService.Allocation(1L, 100L, 2);
        Inventory inv = spy(new Inventory(1L, 100L, 3, 3));
        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 100L)).thenReturn(Optional.of(inv));
        when(inv.release(2)).thenReturn(true);

        boolean success = inventoryService.releaseReservation(List.of(allocation));

        assertTrue(success);
        verify(inv).release(2);
        verify(inventoryRepository).save(inv);
    }

    @Test
    void releaseReservationFailsWhenReleaseRejected() {
        InventoryService.Allocation allocation = new InventoryService.Allocation(1L, 100L, 4);
        Inventory inv = spy(new Inventory(1L, 100L, 3, 2));
        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 100L)).thenReturn(Optional.of(inv));
        when(inv.release(4)).thenReturn(false);

        boolean success = inventoryService.releaseReservation(List.of(allocation));

        assertFalse(success);
    }

    @Test
    void addStockCreatesNewInventoryWhenMissing() {
        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 100L)).thenReturn(Optional.empty());

        Inventory expected = new Inventory(1L, 100L, 5, 0);
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(expected);

        Inventory saved = inventoryService.addStock(1L, 100L, 5);

        assertEquals(5, saved.getQuantityAvailable());
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    void addStockIncrementsExistingInventory() {
        Inventory existing = new Inventory(1L, 100L, 5, 0);
        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 100L)).thenReturn(Optional.of(existing));
        when(inventoryRepository.save(existing)).thenReturn(existing);

        Inventory saved = inventoryService.addStock(1L, 100L, 3);

        assertEquals(8, saved.getQuantityAvailable());
        verify(inventoryRepository).save(existing);
    }
}
