package com.comp5348.bank.service;

import com.comp5348.bank.dto.PaymentTransactionDTO;
import com.comp5348.bank.model.PaymentTransaction;
import com.comp5348.bank.repository.PaymentTransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Business logic for creating and managing transactions.
 */
@Service
public class PaymentTransactionService {
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    public PaymentTransactionService(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    @Transactional
    public PaymentTransactionDTO createPurchaseTransaction(UUID orderID, Double amount) {
        // Perform a transaction between the customer and the store.

        // Ensure amount is non-negative.
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        PaymentTransaction paymentTransaction = new PaymentTransaction(amount, LocalDateTime.now(), "Purchase", "Pending", orderID);
        paymentTransactionRepository.save(paymentTransaction);
        // Once transaction is created, this is where logic to determine whether transaction is successful would go.
        // However, here we just update to confirmed and return to caller.
        paymentTransaction.setStatus("Confirmed");
        paymentTransactionRepository.save(paymentTransaction);
        return new PaymentTransactionDTO(paymentTransaction);
    }

    @Transactional
    public PaymentTransactionDTO createRefundTransaction(UUID orderID, Double amount) {
        // Perform a transaction that returns money to the customer from the store.

        if (amount <= 0){
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        PaymentTransaction paymentTransaction = new PaymentTransaction(amount, LocalDateTime.now(), "Refund", "Pending", orderID);
        paymentTransactionRepository.save(paymentTransaction);

        paymentTransaction.setStatus("Confirmed");
        paymentTransactionRepository.save(paymentTransaction);

        return new PaymentTransactionDTO(paymentTransaction);
    }

    @Transactional
    public PaymentTransactionDTO getPaymentTransaction(Long transactionID) {
        return new PaymentTransactionDTO(paymentTransactionRepository.getPaymentTransactionById(transactionID));
    }
}
