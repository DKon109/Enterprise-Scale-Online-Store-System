package com.comp5348.store.fulfillment.repository;

import com.comp5348.store.fulfillment.model.Fulfillment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

//access to Fulfillment table
public interface FulfillmentRepository extends JpaRepository<Fulfillment, Long> {
    //retrieve all fulfillment by order id
    List<Fulfillment> findByOrder_OrderId(UUID orderId);

    //Lightweight check if fulfillment is existed to the order id
    boolean existsByOrder_OrderId(UUID orderId);

    //fetch one fulfillment
    Optional<Fulfillment> findById(Long id);
}
