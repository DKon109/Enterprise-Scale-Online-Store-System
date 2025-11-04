package com.comp5348.store.fulfillment.model;

import com.comp5348.store.order.model.Order;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fulfillment aggregate: reservation --> commit/ cancel
 */

@Entity
@Table(name = "fulfillment")
public class Fulfillment {
    public enum Status {REQUESTED, RESERVED, COMMITTED, CANCELLED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Version
    private Integer version;

    /** which order this fulfillment belongs to*/
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /*+ Shipping address used when creating Delivery */
    @NotBlank
    @Column(nullable = false, length = 255)
    private String address;

    /** Current status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status =  Status.REQUESTED;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Allocations across warehouses decided at reservation time */
    @ElementCollection
    @CollectionTable(name = "fulfillment_allocation", joinColumns = @JoinColumn(name = "fulfillment_id"))
    private List<AllocationEmb> allocations = new ArrayList<>();

    protected Fulfillment() {}

    public Fulfillment(Order order, String address) {
        this.order = order;
        this.address = address;
    }

    // status helpers
    public void markReserved() {
        this.status = Status.RESERVED;
        this.reservedAt = LocalDateTime.now();
    }

    public void markCommitted() {
        this.status = Status.COMMITTED;
        this.committedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = Status.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    //Accessors
    public Long getId() {
        return id;
    }
    public Integer getVersion() {
        return version;
    }
    public Order getOrder() {
        return order;
    }
    public String getAddress() {
        return address;
    }
    public Status getStatus() {
        return status;
    }
    public LocalDateTime getReservedAt() {
        return reservedAt;
    }
    public LocalDateTime getCommittedAt() {
        return committedAt;
    }
    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
    public List<AllocationEmb> getAllocations(){
        return allocations;
    }

    public void setAllocations(List<AllocationEmb> allocations) {
        this.allocations = allocations;
    }

    /**Embeddable allocation record */
    @Embeddable
    public static class AllocationEmb {
        @NotNull @Column(name = "warehouse_id", nullable = false)
        public Long warehouseId;
        @NotNull @Column(name = "product_id", nullable = false)
        public Long productId;
        @Column(name = "quantity", nullable = false)
        public int quantity;

        protected AllocationEmb() {}
        public AllocationEmb(Long wh, Long pr, int qu) {
            this.warehouseId = wh;
            this.productId = pr;
            this.quantity = qu;
        }
    }
}
