package com.comp5348.store.inventory.repository;
import com.comp5348.store.inventory.model.Inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findByProductId(Long productId);
    Optional<Inventory> findByWarehouseIdAndProductId(Long warehouseId, Long productId);
}