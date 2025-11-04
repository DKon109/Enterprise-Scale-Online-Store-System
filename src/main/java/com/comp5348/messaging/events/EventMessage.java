package com.comp5348.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event message published to RabbitMQ queues.
 *
 * Used for asynchronous communication between Store and external services.
 * Listeners consume these events and react accordingly.
 *
 * Compliance: §79-80 (Reliable asynchronous messaging), §242 (Idempotency), §246 (Correlation tracking)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {

    // ===== Core Event Data =====
    private String type;                    // e.g., "payment.success", "shipment.requested"
    private UUID orderId;                   // Order ID (changed from Long to UUID)
    private Double amount;                  // Amount in dollars
    private String customerEmail;           // Customer email for notifications
    private String description;             // Human-readable description

    // ===== Tracing & Correlation =====
    private String correlationId;           // Trace across services (§246)
    private String idempotencyKey;          // Prevent duplicate processing (§242)

    // ===== Timing & Retry =====
    private LocalDateTime timestamp;        // When event was created
    private int retryCount;                 // How many times this event has been retried

    // ===== Convenience Constructor =====
    public EventMessage(String type, UUID orderId, String description) {
        this.type = type;
        this.orderId = orderId;
        this.description = description;
        this.timestamp = LocalDateTime.now();
        this.retryCount = 0;
    }
}
