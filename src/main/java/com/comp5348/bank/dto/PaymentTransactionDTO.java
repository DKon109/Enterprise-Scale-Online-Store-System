package com.comp5348.bank.dto;

import com.comp5348.bank.model.PaymentTransaction;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    private LocalDateTime time;
    private UUID orderID;

    public PaymentTransactionDTO(PaymentTransaction entity) {
        this.id = entity.getId();
        this.amount = entity.getAmount();
        this.type = entity.getType();
        this.status = entity.getStatus();
        this.bankReferenceID = entity.getBankReferenceID();
        this.time = entity.getTimeStamp();
        this.orderID = entity.getOrderID();
    }
}
