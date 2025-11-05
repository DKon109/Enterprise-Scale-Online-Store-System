package com.comp5348.delivery.controller;

import com.comp5348.delivery.service.DeliveryService;
import com.comp5348.store.fulfillment.service.FulfillmentService;
import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.messaging.warehouse.WarehouseEventPublisher;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/delivery")
public class DeliveryWebhookController {

    private final DeliveryService deliveryService;
    private final FulfillmentService fulfillmentService;
    private final NotificationServicePort notificationService;
    private final WarehouseEventPublisher warehouseEventPublisher;

    public DeliveryWebhookController(
            DeliveryService deliveryService,
            FulfillmentService fulfillmentService,
            NotificationServicePort notificationService,
            WarehouseEventPublisher warehouseEventPublisher) {
        this.deliveryService = deliveryService;
        this.fulfillmentService = fulfillmentService;
        this.notificationService = notificationService;
        this.warehouseEventPublisher = warehouseEventPublisher;
    }

    @PostMapping("/request-received")
    public ResponseEntity<WebhookResponse> requestReceived(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody DeliveryWebhookRequest payload) {
        deliveryService.schedulePostAcknowledgementUpdate(payload.orderId(), payload.trackingNumber());
        sendNotification(payload, "REQUEST_RECEIVED");
        publishWarehouseEvent(payload, "item.preparing", "Warehouse acknowledged shipment request", correlationId);
        return ack("REQUEST_RECEIVED");
    }

    @PostMapping("/picked-up")
    public ResponseEntity<WebhookResponse> pickedUp(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody DeliveryWebhookRequest payload) {
        deliveryService.markPickedUp(payload.trackingNumber());

        // Commit stock reservation when picked up
        // Find fulfillment for this order and commit it
        var fulfillments = fulfillmentService.listByOrderId(payload.orderId());
        for (var fulfillment : fulfillments) {
            fulfillmentService.commit(fulfillment.getId());
        }

        sendNotification(payload, "PICKED_UP");
        publishWarehouseEvent(payload, "item.shipped", "Warehouse picked up shipment", correlationId);
        return ack("PICKED_UP");
    }

    @PostMapping("/in-transit")
    public ResponseEntity<WebhookResponse> inTransit(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody DeliveryWebhookRequest payload) {
        deliveryService.markInTransit(payload.trackingNumber());
        sendNotification(payload, "IN_TRANSIT");
        publishWarehouseEvent(payload, "item.in_transit", "Shipment in transit", correlationId);
        return ack("IN_TRANSIT");
    }

    @PostMapping("/delivered")
    public ResponseEntity<WebhookResponse> delivered(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody DeliveryWebhookRequest payload) {
        deliveryService.markDelivered(payload.trackingNumber());
        sendNotification(payload, "DELIVERED");
        publishWarehouseEvent(payload, "item.delivered", "Shipment delivered to customer", correlationId);
        return ack("DELIVERED");
    }

    private void sendNotification(DeliveryWebhookRequest payload, String template) {
        notificationService.send(
                payload.orderId(),
                template,
                Map.of(
                        "trackingNumber", payload.trackingNumber(),
                        "status", payload.status()));
    }

    private void publishWarehouseEvent(DeliveryWebhookRequest payload, String eventType, String description, String correlationId) {
        warehouseEventPublisher.publishDeliveryStatus(
                payload.orderId(),
                eventType,
                description,
                correlationId,
                payload.trackingNumber());
    }

    private ResponseEntity<WebhookResponse> ack(String status) {
        return ResponseEntity.ok(new WebhookResponse(status, "processed"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryWebhookRequest(
            @NotNull UUID orderId,
            @NotBlank String trackingNumber,
            @NotBlank String status,
            String timestamp) {
    }

    public record WebhookResponse(String status, String message) {}
}
