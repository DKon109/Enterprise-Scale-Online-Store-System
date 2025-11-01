package com.comp5348.bank.service;

import com.comp5348.bank.dto.PaymentTransactionDTO;
import com.comp5348.bank.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Business logic for creating and managing transactions.
 */
@Service
public class PaymentTransactionService {
    private final PaymentTransactionRepository paymentTransactionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public PaymentTransactionService(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    @Transactional
    public PaymentTransactionDTO performTransaction(Double amount) {
        // Perform a transaction between the customer and the store.
        // Will need to take in customer details, an amount, and whether it is a payment or a refund.
        if (amount <= 0){
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return null;
    }
}
