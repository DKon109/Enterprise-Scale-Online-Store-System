package com.comp5348.warehouse.repository;

import com.comp5348.warehouse.model.Warehouse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarehouseRepository {
    private final Map<Long, Warehouse> warehouses = new ConcurrentHashMap<>();

    public Optional<Warehouse> findById(long id){
        return Optional.ofNullable(warehouses.get(id));
    }

    public Collection<Warehouse> findAll(){
        return warehouses.values();
    }

    public void save(Warehouse wh){
        warehouses.put(wh.getWarehouseId(), wh);
    }

    //To demo, initial load
    public void preload(Warehouse... list){
        for (Warehouse wh : list){
            save(wh);
        }
    }


}
