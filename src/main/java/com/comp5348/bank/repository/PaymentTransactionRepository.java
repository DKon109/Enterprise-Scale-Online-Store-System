package com.comp5348.bank.repository;

import com.comp5348.bank.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data Access Object for payment_transaction database table.
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction,Long> {
    PaymentTransaction getPaymentTransactionById(long id);
}
