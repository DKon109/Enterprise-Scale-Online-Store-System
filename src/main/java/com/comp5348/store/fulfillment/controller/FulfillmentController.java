package com.comp5348.store.fulfillment.controller;

import com.comp5348.store.fulfillment.model.Fulfillment;
import com.comp5348.store.fulfillment.service.FulfillmentService;
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
import java.util.UUID;

/**
 * Fulfillment REST API
 * - POST   /fulfillments/reserve             : create a new fulfillment with RESERVED status
 * - POST   /fulfillments/{id}/commit         : commit the fulfillment (inventory confirmed + delivery created)
 * - POST   /fulfillments/{id}/cancel         : cancel the fulfillment
 * - GET    /fulfillments?orderId=            : list all fulfillments by orderId
 * - GET    /fulfillments/{id}                : fetch a single fulfillment by id
 */
@RestController
@RequestMapping("/fulfillments")
@Validated
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * Create and reserve a new fulfillment
     */
    @PostMapping("/reserve")
    public ResponseEntity<FulfillmentResponse> reserve(@RequestBody @Valid ReserveRequest body) {
        Fulfillment created = fulfillmentService.reserve(
                body.orderId, body.address, body.productId, body.quantity);
        FulfillmentResponse res = FulfillmentResponse.from(created);

        // Return HTTP 201 with the location of the new resource
        return ResponseEntity.created(URI.create("/fulfillments/" + created.getId())).body(res);
    }

    /**
     * Commit the fulfillment (status -> COMMITTED)
     */
    @PostMapping("/{id}/commit")
    public ResponseEntity<Void> commit(@PathVariable("id") @Min(1) Long fulfillmentId) {
        boolean ok = fulfillmentService.commit(fulfillmentId);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Cancel the fulfillment (status -> CANCELLED)
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable("id") @Min(1) Long fulfillmentId) {
        boolean ok = fulfillmentService.cancel(fulfillmentId);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Retrieve all fulfillments by orderId
     */
    @GetMapping
    public ResponseEntity<List<FulfillmentResponse>> listByOrder(
            @RequestParam @NotNull @Min(1) UUID orderId) {
        List<FulfillmentResponse> list = fulfillmentService.listByOrderId(orderId)
                .stream().map(FulfillmentResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    /**
     * Retrieve a single fulfillment by its ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<FulfillmentResponse> getById(@PathVariable("id") @Min(1) UUID id) {
        List<FulfillmentResponse> found = fulfillmentService.listByOrderId(id)
                .stream().map(FulfillmentResponse::from).toList();
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(found.get(0));
    }

    //  DTO CLASSES

    /** DTO for creating/reserving fulfillment */
    public static class ReserveRequest {
        @NotNull @Min(1) public UUID orderId;
        @NotBlank public String address;
        @NotNull @Min(1) public Long productId;
        @Min(1) public int quantity;

        public ReserveRequest() {}
    }

    /** DTO for response body */
    public static class FulfillmentResponse {
        public UUID id;
        public UUID orderId;
        public String address;
        public String status;
        public String reservedAt;
        public String committedAt;
        public String cancelledAt;

        static FulfillmentResponse from(Fulfillment f) {
            FulfillmentResponse res = new FulfillmentResponse();
            res.id = f.getId();
            res.orderId = f.getOrder() != null ? f.getOrder().getOrderId() : null;
            res.address = f.getAddress();
            res.status = f.getStatus().name();
            res.reservedAt = f.getReservedAt() == null ? null : f.getReservedAt().toString();
            res.committedAt = f.getCommittedAt() == null ? null : f.getCommittedAt().toString();
            res.cancelledAt = f.getCancelledAt() == null ? null : f.getCancelledAt().toString();
            return res;
        }
    }
}