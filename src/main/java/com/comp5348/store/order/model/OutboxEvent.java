package com.comp5348.store.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbox pattern entity used to persist notification events before dispatching.
 *
 * Entries are picked up by {@code OutboxWorker} to deliver notifications reliably.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Column(nullable = false, length = 120)
    private String template;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean sent;

    @Column
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private int retryCount;

    protected OutboxEvent() {
        // For JPA
    }

    public OutboxEvent(UUID orderId, String template, String payload) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.template = Objects.requireNonNull(template, "template must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.sent = false;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getTemplate() {
        return template;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isSent() {
        return sent;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void markSent() {
        this.sent = true;
        this.sentAt = LocalDateTime.now();
    }

    public void markAttemptFailed() {
        this.retryCount += 1;
    }
}
