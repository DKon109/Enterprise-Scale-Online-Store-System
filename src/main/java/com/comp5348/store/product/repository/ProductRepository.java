package com.comp5348.store.product.repository;

import com.comp5348.store.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Product DAO. Names are unique to simplify demo/catalog use.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByName(String name);
    List<Product> findByNameIgnoreCaseContaining(String q);
    boolean existsByName(String name);
}