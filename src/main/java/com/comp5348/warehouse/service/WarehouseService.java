package com.comp5348.warehouse.service;

import com.comp5348.warehouse.model.Warehouse;
import com.comp5348.warehouse.repository.WarehouseRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
public class WarehouseService {
    private final WarehouseRepository repo;

    public WarehouseService(WarehouseRepository repo) {
        this.repo = repo;
    }

    //fetch all
    public Collection<Warehouse> listWarehouses(){
        return repo.findAll();
    }

    //retrieve by id
    public Optional<Warehouse> getWarehouse(Long id){
        return repo.findById(id);
    }

    //preload / add
    public Warehouse addWarehouse(String location){
        Warehouse w = new Warehouse(location);
        return repo.save(w);
    }
}