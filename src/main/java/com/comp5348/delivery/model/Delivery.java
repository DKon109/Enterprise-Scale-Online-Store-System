package com.comp5348.delivery.model;


import com.comp5348.store.order.model.Order;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery")
public class Delivery {
    public enum Status {
        PENDING, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    /**
     * delivery option
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Which warehouse to deliver
     */
    @NotNull
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    /**
     * Address to deliver
     */
    @NotBlank
    @Column(nullable = false, length = 255)
    private String address;

    /**
     * status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING;

    /**
     * Tracking number
     */
    @Column(name = "tracking_number", length = 128, unique = false)
    private String trackingNumber;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    protected Delivery() {
    }

    public Delivery(Order order, Long warehouseId, String address, String trackingNumber) {
        this.order = order;
        this.warehouseId = warehouseId;
        this.address = address;
        this.trackingNumber = trackingNumber;
        this.status = Status.PENDING;
    }

    // Domain helpers
    public void markDispatched() {
        this.status = Status.DISPATCHED;
        this.dispatchedAt = LocalDateTime.now();
    }

    public void markInTransit() {
        this.status = Status.IN_TRANSIT;
    }

    public void markDelivered() {
        this.status = Status.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = Status.CANCELED;
    }

    //GETTERS, SETTERS
    public Long getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    public Order getOrder() {
        return order;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getAddress() {
        return address;
    }

    public Status getStatus() {
        return status;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public LocalDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}