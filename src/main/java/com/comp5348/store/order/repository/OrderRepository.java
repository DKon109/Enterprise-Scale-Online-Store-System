package com.comp5348.store.order.repository;

import com.comp5348.store.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Order getOrderById(Long id);
}
