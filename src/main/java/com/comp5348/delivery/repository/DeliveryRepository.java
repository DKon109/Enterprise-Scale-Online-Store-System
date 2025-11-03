package com.comp5348.delivery.repository;
import com.comp5348.delivery.model.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the delivery table
 */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    // Retrieve all deliveries associated with the same order
    List<Delivery> findByOrder_Id(long orderId);

    //Search the delivery by tracking number
    Optional<Delivery> findByTrackingNumber(String trackingNumber);

    //Lightweight check to see if a delivery exists for the given order
    boolean existsByOrder_Id(long orderId);
}
