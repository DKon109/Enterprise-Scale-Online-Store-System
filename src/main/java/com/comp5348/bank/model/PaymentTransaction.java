package com.comp5348.bank.model;

import com.comp5348.bank.repository.PaymentTransactionRepository;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.DateTimeException;
import java.time.LocalDateTime;

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

    public PaymentTransaction(Double amount, LocalDateTime timeStamp, String type, String status, String bankReferenceID) {
        this.amount = amount;
        this.timeStamp = timeStamp;
        this.type = type;
        this.status = status;
        this.bankReferenceID = bankReferenceID;
    }

    public PaymentTransaction() {}
}
