package com.comp5348.bank.model;

import com.comp5348.bank.repository.PaymentTransactionRepository;
import com.comp5348.store.order.model.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.DateTimeException;
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
    @Setter
    private String status;

    @Column
    @GeneratedValue(generator = "uuid")
    private String bankReferenceID;

    @Column
    private UUID orderID;

    public PaymentTransaction(Double amount, LocalDateTime timeStamp, String type, String status, UUID orderID) {
        this.amount = amount;
        this.timeStamp = timeStamp;
        this.type = type;
        this.status = status;
        this.orderID = orderID;
    }

    public PaymentTransaction() {}
}
