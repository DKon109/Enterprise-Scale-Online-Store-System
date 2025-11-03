package com.comp5348.store.product.controller;

import com.comp5348.store.product.model.Product;
import com.comp5348.store.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * Product REST API
 * - POST   /products                  : create a product
 * - GET    /products/{id}             : get one
 * - GET    /products?q=               : list (optional query by name)
 * - PUT    /products/{id}             : update
 * - DELETE /products/{id}             : delete
 *
 * Notes:
 *  - IDs are Long, consistent with Inventory/Fulfillment/Delivery.
 *  - This controller returns DTO-style responses but keeps it simple
 *    by mapping from the entity to a thin response object.
 */
@RestController
@RequestMapping("/products")
@Validated
public class ProductController {

    private final ProductService svc;

    public ProductController(ProductService svc) {
        this.svc = svc;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@RequestBody @Valid CreateProductRequest body) {
        Product created = svc.create(body.name, body.price, body.description);
        return ResponseEntity
                .created(URI.create("/products/" + created.getId()))
                .body(ProductResponse.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> get(@PathVariable Long id) {
        Product p = svc.get(id);
        return ResponseEntity.ok(ProductResponse.from(p));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> list(@RequestParam(required = false) String q) {
        List<ProductResponse> res = svc.list(q).stream().map(ProductResponse::from).toList();
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id,
                                                  @RequestBody @Valid UpdateProductRequest body) {
        Product updated = svc.update(id, body.name, body.price, body.description);
        return ResponseEntity.ok(ProductResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return ResponseEntity.noContent().build();
    }

    // DTOs

    /** Request DTO for create. */
    public static class CreateProductRequest {
        @NotBlank @Size(max = 255) public String name;
        @DecimalMin(value = "0.0", inclusive = false) public BigDecimal price;
        @Size(max = 1024) public String description;
        public CreateProductRequest() {}
    }

    /** Request DTO for update (all fields optional). */
    public static class UpdateProductRequest {
        @Size(max = 255) public String name;
        @DecimalMin(value = "0.0", inclusive = false) public BigDecimal price;
        @Size(max = 1024) public String description;
        public UpdateProductRequest() {}
    }

    /** Thin response DTO to avoid exposing JPA internals. */
    public static class ProductResponse {
        public Long id;
        public String name;
        public BigDecimal price;
        public String description;

        static ProductResponse from(Product p) {
            ProductResponse r = new ProductResponse();
            r.id = p.getId();
            r.name = p.getName();
            r.price = p.getPrice();
            r.description = p.getDescription();
            return r;
        }
    }
}