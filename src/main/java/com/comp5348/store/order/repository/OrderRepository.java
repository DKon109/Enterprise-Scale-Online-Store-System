package com.comp5348.store.order.repository;

import com.comp5348.store.order.model.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Order persistence.
 *
 * Provides CRUD operations and custom queries for Order entities.
 * Extends JpaRepository for Spring Data JPA support.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Find all orders for a specific customer.
     *
     * @param customerId the customer ID
     * @return list of orders for the customer
     */
    List<Order> findByCustomerId(UUID customerId);

    /**
     * Check if an order exists by ID.
     *
     * @param orderId the order ID
     * @return true if order exists, false otherwise
     */
    boolean existsById(UUID orderId);

    /**
     * Find an order by ID, throwing exception if not found.
     *
     * @param orderId the order ID
     * @return the order
     * @throws org.springframework.data.crossstore.ChangeSetPersister.NotFoundException if not found
     */
    Optional<Order> findById(UUID orderId);

    /**
     * Find an order by the idempotency request identifier.
     *
     * @param requestId the idempotency key supplied by the client
     * @return optional order if the request was processed before
     */
    Optional<Order> findByRequestId(String requestId);
}
