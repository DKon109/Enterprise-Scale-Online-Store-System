package com.comp5348.messaging.events;

public class OrderEvent {
    private String type; // e.g. "ORDER_CANCELLED", "REFUND_COMPLETED"
    private Long orderId;
    private Double amount;
    private String email;
    // getters, setters, constructor, toString...
}
