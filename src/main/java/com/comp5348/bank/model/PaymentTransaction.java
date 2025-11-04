package com.comp5348.bank.model;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
public class PaymentTransaction {
    @Id
    @GeneratedValue
    private long id;

    @Version
    private int version;

    @Column(nullable = false)
    private Double amount = 0.0;

    @Column
    private LocalDateTime timeStamp;

    @Column
    private String type;

    @Column
    private String status;

    @Column
    private String bankReferenceID;

    @Column
    private UUID orderID;

    // ===== NEW FIELDS FOR COMP5348 COMPLIANCE =====

    /**
     * Idempotency key for preventing duplicate payment processing.
     * If same idempotencyKey is used, returns existing transaction instead of creating new one.
     * Required by: §242 (Idempotency keys on all state-changing calls)
     */
    @Column(unique = true)
    private String idempotencyKey;

    /**
     * Correlation ID for request tracing across services.
     * Enables tracking a single order through Bank → DeliveryCo → Email services.
     * Required by: §246, §292 (Correlation ID / Saga ID tracking through all events)
     */
    @Column
    private String correlationId;

    public PaymentTransaction(Double amount, LocalDateTime timeStamp, String type, String status, UUID orderID) {
        this.amount = amount;
        this.timeStamp = timeStamp;
        this.type = type;
        this.status = status;
        this.orderID = orderID;
        this.bankReferenceID = UUID.randomUUID().toString();
    }

    public PaymentTransaction() {}

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
