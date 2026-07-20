package com.comp5348.store.config;

import com.comp5348.store.inventory.model.Inventory;
import com.comp5348.store.inventory.repository.InventoryRepository;
import com.comp5348.store.product.model.Product;
import com.comp5348.store.product.repository.ProductRepository;
import com.comp5348.warehouse.model.Warehouse;
import com.comp5348.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataBootstrap {

    @Bean
    CommandLineRunner demoCatalogInitializer(
            ProductRepository products,
            WarehouseRepository warehouses,
            InventoryRepository inventory) {
        return args -> seed(products, warehouses, inventory);
    }

    @Transactional
    void seed(ProductRepository products, WarehouseRepository warehouses, InventoryRepository inventory) {
        if (products.count() > 0 || warehouses.count() > 0) {
            return;
        }

        Warehouse sydney = warehouses.save(new Warehouse("Sydney Distribution Hub"));
        Warehouse melbourne = warehouses.save(new Warehouse("Melbourne Micro-Fulfilment"));
        Warehouse brisbane = warehouses.save(new Warehouse("Brisbane Warehouse"));

        List<Product> catalog = products.saveAll(List.of(
                new Product("AeroPress Coffee Maker", new BigDecimal("64.90"),
                        "A compact brewer for smooth coffee at home or on the road."),
                new Product("Recycled Canvas Daypack", new BigDecimal("89.00"),
                        "A durable 18L everyday pack made from recycled canvas."),
                new Product("Stoneware Travel Mug", new BigDecimal("38.50"),
                        "Double-wall ceramic comfort with a secure silicone lid."),
                new Product("Merino Trail Socks", new BigDecimal("29.90"),
                        "Breathable, cushioned merino socks for long days outside."),
                new Product("Pocket Field Notebook", new BigDecimal("16.00"),
                        "Weather-resistant pages for ideas, lists and trail notes."),
                new Product("USB-C Desk Lamp", new BigDecimal("74.00"),
                        "Warm, dimmable task lighting in a minimal aluminium body.")));

        for (int i = 0; i < catalog.size(); i++) {
            Product product = catalog.get(i);
            inventory.save(new Inventory(sydney.getId(), product.getId(), 4 + i, 0));
            inventory.save(new Inventory(melbourne.getId(), product.getId(), 3 + (i % 3), 0));
            if (i % 2 == 0) {
                inventory.save(new Inventory(brisbane.getId(), product.getId(), 5, 0));
            }
        }
    }
}
