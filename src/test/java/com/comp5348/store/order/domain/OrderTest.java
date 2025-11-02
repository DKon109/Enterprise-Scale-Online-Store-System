package com.comp5348.store.order.domain;

import com.comp5348.store.order.domain.model.Order;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

class OrderTest {

    @Test
    void transitionsFollowHappyPath() {
        Order order = new Order(UUID.randomUUID(), "cust-1", "SKU-1", 2);

        order.markReserved();
        assertEquals(Order.Status.RESERVED, order.getStatus());

        order.markPaid();
        assertEquals(Order.Status.PAID, order.getStatus());

        order.markShipmentRequested();
        assertEquals(Order.Status.SHIPMENT_REQUESTED, order.getStatus());

        order.markDelivered();
        assertEquals(Order.Status.DELIVERED, order.getStatus());
    }

    @Test
    void cancelDisallowedAfterShipmentRequest() {
        Order order = new Order(UUID.randomUUID(), "cust-1", "SKU-1", 2);
        order.markReserved();
        order.markPaid();
        order.markShipmentRequested();

        assertFalse(order.canCancel());
        assertThrows(IllegalStateException.class, order::cancel);
    }
}
