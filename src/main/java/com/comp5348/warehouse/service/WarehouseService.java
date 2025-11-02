package com.comp5348.warehouse.service;

import com.comp5348.warehouse.model.Warehouse;
import com.comp5348.warehouse.repository.WarehouseRepository;

import java.util.Collection;
import java.util.Optional;

public class WarehouseService {
    private final WarehouseRepository repo;

    public WarehouseService(WarehouseRepository repo) {
        this.repo = repo;
    }

    public Collection<Warehouse> listWarehouses(){
        return repo.findAll();
    }

    public Optional<Warehouse> getWarehouse(Long id){
        return repo.findById(id);
    }

    //preload / add
    public Warehouse addWarehouse(Long id, String location){
        Warehouse w = new Warehouse(id, location);
        repo.save(w);
        return w;

    }
}
