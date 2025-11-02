package com.comp5348.bank.model;

import com.comp5348.bank.repository.PaymentTransactionRepository;
<<<<<<< HEAD
import com.comp5348.store.order.model.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
=======
import jakarta.persistence.*;
import lombok.Getter;
>>>>>>> b152dbe (Added basic structure of paymentTransaction class. Included springboot application dependencies in gradle build file.)

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
<<<<<<< HEAD
    @Setter
    private String status;

    @Column
    @GeneratedValue(generator = "uuid")
    private String bankReferenceID;

    @ManyToOne
    @JoinColumn
    private Order order;

    public PaymentTransaction(Double amount, LocalDateTime timeStamp, String type, String status, Order order) {
=======
    private String status;

    @Column
    private String bankReferenceID;

    public PaymentTransaction(Double amount, LocalDateTime timeStamp, String type, String status, String bankReferenceID) {
>>>>>>> b152dbe (Added basic structure of paymentTransaction class. Included springboot application dependencies in gradle build file.)
        this.amount = amount;
        this.timeStamp = timeStamp;
        this.type = type;
        this.status = status;
<<<<<<< HEAD
        this.order = order;
=======
        this.bankReferenceID = bankReferenceID;
>>>>>>> b152dbe (Added basic structure of paymentTransaction class. Included springboot application dependencies in gradle build file.)
    }

    public PaymentTransaction() {}
}
