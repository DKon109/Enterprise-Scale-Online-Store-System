package com.comp5348.bank.dto;

import com.comp5348.bank.model.PaymentTransaction;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for PaymentTransaction
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentTransactionDTO {
    private long id;
    private Double amount;
    private String type;
    private String status;
    private String bankReferenceID;
    private LocalDateTime timestamp;

    @JsonProperty("orderId")
    private UUID orderId;

    private String idempotencyKey;
    private String correlationId;

    public PaymentTransactionDTO(PaymentTransaction entity) {
        this.id = entity.getId();
        this.amount = entity.getAmount();
        this.type = entity.getType();
        this.status = entity.getStatus();
        this.bankReferenceID = entity.getBankReferenceID();
        this.timestamp = entity.getTimeStamp();
        this.orderId = entity.getOrderID();
        this.idempotencyKey = entity.getIdempotencyKey();
        this.correlationId = entity.getCorrelationId();
    }
}
