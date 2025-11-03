package com.comp5348.store.product.service;

import com.comp5348.store.product.model.Product;
import com.comp5348.store.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business logic for products (catalog).  Kept independent so other bounded
 * contexts (Order, Inventory) can reference Product IDs without tight coupling.
 */
@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    /** Create a new product (name must be unique). */
    @Transactional
    public Product create(String name, BigDecimal price, String description) {
        if (repo.existsByName(name)) {
            throw new IllegalArgumentException("Product already exists: " + name);
        }
        return repo.save(new Product(name, price, description));
    }

    /** Read single product (throws if not found). */
    @Transactional(readOnly = true)
    public Product get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    /** Simple listing with optional name filter. */
    @Transactional(readOnly = true)
    public List<Product> list(String q) {
        if (q == null || q.isBlank()) {
            return repo.findAll();
        }
        return repo.findByNameIgnoreCaseContaining(q);
    }

    /** Update mutable fields. */
    @Transactional
    public Product update(Long id, String name, BigDecimal price, String description) {
        Product p = get(id);
        if (name != null && !name.isBlank() && !name.equals(p.getName())) {
            // ensure new name is still unique
            if (repo.existsByName(name)) {
                throw new IllegalArgumentException("Product already exists: " + name);
            }
            p.setName(name);
        }
        if (price != null) p.setPrice(price);
        if (description != null) p.setDescription(description);
        return repo.save(p);
    }

    /** Remove a product. In a real system you might soft-delete. */
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new IllegalArgumentException("Product not found: " + id);
        }
        repo.deleteById(id);
    }
}