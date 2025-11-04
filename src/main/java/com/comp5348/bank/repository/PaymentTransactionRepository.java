package com.comp5348.bank.repository;

import com.comp5348.bank.model.PaymentTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data Access Object for payment_transaction database table.
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction,Long> {
    PaymentTransaction getPaymentTransactionById(long id);

    Optional<PaymentTransaction> findTopByOrderIDAndTypeOrderByTimeStampDesc(UUID orderID, String type);

    /**
     * Find transaction by idempotency key.
     * Used to prevent duplicate payment processing.
     * Required by: §242 (Idempotency keys on all state-changing calls)
     */
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find transaction by order ID and idempotency key.
     * Provides additional filtering for idempotency checks.
     */
    Optional<PaymentTransaction> findByOrderIDAndIdempotencyKey(UUID orderID, String idempotencyKey);
}
