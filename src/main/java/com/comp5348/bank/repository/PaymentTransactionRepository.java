package com.comp5348.bank.repository;

<<<<<<< HEAD
import com.comp5348.bank.dto.PaymentTransactionDTO;
=======
>>>>>>> b152dbe (Added basic structure of paymentTransaction class. Included springboot application dependencies in gradle build file.)
import com.comp5348.bank.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data Access Object for payment_transaction database table.
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction,Long> {
<<<<<<< HEAD
    PaymentTransaction getPaymentTransactionById(long id);
=======
>>>>>>> b152dbe (Added basic structure of paymentTransaction class. Included springboot application dependencies in gradle build file.)
}
