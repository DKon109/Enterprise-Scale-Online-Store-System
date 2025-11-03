package com.comp5348.delivery.controller;

import com.comp5348.delivery.model.Delivery;
import com.comp5348.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 *  Delivery REST API
 * - POST   /deliveries                      : create the delivery
 * - GET    /deliveries?orderId=             : fetch all delivery by orderId
 * - GET    /deliveries/track/{tracking}     : retrieve by the tracking number
 * - POST   /deliveries/{id}/dispatch        : status -> dispatched
 * - POST   /deliveries/{id}/deliver         : status -> delivered
 * - POST   /deliveries/{id}/cancel          : cancel
 */
@RestController
@RequestMapping("/deliveries")
@Validated
public class DeliveryController{
    private final DeliveryService deliveryService;
    public DeliveryController(DeliveryService deliveryService){
        this.deliveryService = deliveryService;
    }

    /**create a new delivery record
     */
    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@RequestBody @Valid CreateDeliveryRequest body){
        Delivery created = deliveryService.createDelivery(
                body.orderId, body.warehouseId, body.address, body.trackingNumber);

        DeliveryResponse res = DeliveryResponse.from(created);
        //Location header points to a retrievable endpoint (optional enhancement)
        return ResponseEntity.created(URI.create("/deliveries?orderId=" + created.getOrder().getOrderId())).body(res);
    }

    /**Retrieve all deliveries associated with a specific order
     */
    @GetMapping
    public ResponseEntity<List<DeliveryResponse>> listByOrder(
            @RequestParam @NotNull @Min(1) UUID orderId) {
        List<DeliveryResponse> list = deliveryService.getDeliveriesByOrderId(orderId).stream().map(DeliveryResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    /**fetch the delivery by tracking number
     */

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<DeliveryResponse> getByTracking(
            @PathVariable @NotBlank String trackingNumber){
        Optional<Delivery> opt = deliveryService.getByTrackingNumber(trackingNumber);
        return opt.map(d -> ResponseEntity.ok(DeliveryResponse.from(d))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Status --> DISPATCHED and record the timestamp
     */
    @PostMapping("/{id}/dispatch")
    public ResponseEntity<Void> markDispatched(@PathVariable("id") @Min(1) Long deliveryId){
        boolean ok = deliveryService.markDispatched(deliveryId);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Status --> DELIVERED and record the timestamp
     */
    @PostMapping("/{id}/deliver")
    public ResponseEntity<Void> markDelivered(@PathVariable("id") @Min(1) Long deliveryId){
        boolean ok = deliveryService.markDelivered(deliveryId);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * cancel of delivery
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable("id") @Min(1) Long deliveryId){
        boolean ok = deliveryService.cancelDelivery(deliveryId);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * DTO class for incoming delivery creation requests
     * Validation is handled by Bean Validation
     */
    public static class CreateDeliveryRequest{
        @NotNull @Min(1) public UUID orderId;
        @NotNull @Min(1) public Long warehouseId;
        @NotBlank public  String address;
        public String trackingNumber;
        public CreateDeliveryRequest(){}
    }

    /**
     * DTO class for delivery responses
     */
    public static class DeliveryResponse{
        public Long id;
        public UUID orderId;
        public Long warehouseId;
        public String address;
        public String status;
        public String trackingNumber;
        public String dispatchedAt;
        public String deliveredAt;

        static DeliveryResponse from(Delivery del){
            DeliveryResponse dr = new DeliveryResponse();
            dr.id = del.getId();
            dr.orderId = (del.getOrder() != null ? del.getOrder().getOrderId() : null);
            dr.warehouseId = del.getWarehouseId();
            dr.address = del.getAddress();
            dr.status = del.getStatus().name();
            dr.trackingNumber = del.getTrackingNumber();
            dr.dispatchedAt = del.getDispatchedAt() == null ? null : del.getDispatchedAt().toString();
            dr.deliveredAt = del.getDeliveredAt() == null ? null : del.getDeliveredAt().toString();
            return dr;
        }
    }

}