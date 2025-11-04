package com.comp5348.bank.service;

import com.comp5348.bank.dto.PaymentTransactionDTO;
import com.comp5348.bank.model.PaymentTransaction;
import com.comp5348.bank.repository.PaymentTransactionRepository;
import com.comp5348.messaging.bank.BankMessageProducer;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Business logic for creating and managing transactions.
 */
@Service
public class PaymentTransactionService {
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BankMessageProducer bankMessageProducer;

    @Autowired
    public PaymentTransactionService(
            PaymentTransactionRepository paymentTransactionRepository,
            BankMessageProducer bankMessageProducer) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.bankMessageProducer = bankMessageProducer;
    }

    @Transactional
    public TransactionResult createPurchaseTransaction(UUID orderID, Double amount, String idempotencyKey, String correlationId) {
        validateOrder(orderID);
        validateAmount(amount);
        String resolvedIdempotencyKey = requireIdempotencyKey(idempotencyKey);

        return paymentTransactionRepository.findByIdempotencyKey(resolvedIdempotencyKey)
                .map(existing -> new TransactionResult(new PaymentTransactionDTO(existing), false))
                .orElseGet(() -> {
                    PaymentTransaction paymentTransaction = new PaymentTransaction(
                            amount,
                            LocalDateTime.now(),
                            "Purchase",
                            "Confirmed",
                            orderID);
                    paymentTransaction.setIdempotencyKey(resolvedIdempotencyKey);
                    paymentTransaction.setCorrelationId(trimToNull(correlationId));

                    PaymentTransaction saved = paymentTransactionRepository.save(paymentTransaction);
                    bankMessageProducer.publishTransactionEvent(saved, "payment.success");
                    return new TransactionResult(new PaymentTransactionDTO(saved), true);
                });
    }

    @Transactional
    public TransactionResult createRefundTransaction(UUID orderID, Double amount, String idempotencyKey, String correlationId) {
        validateOrder(orderID);
        if (amount != null) {
            validateAmount(amount);
        }
        String resolvedIdempotencyKey = requireIdempotencyKey(idempotencyKey);

        return paymentTransactionRepository.findByIdempotencyKey(resolvedIdempotencyKey)
                .map(existing -> new TransactionResult(new PaymentTransactionDTO(existing), false))
                .orElseGet(() -> {
                    Double resolvedAmount = resolveRefundAmount(orderID, amount);

                    PaymentTransaction paymentTransaction = new PaymentTransaction(
                            resolvedAmount,
                            LocalDateTime.now(),
                            "Refund",
                            "Confirmed",
                            orderID);
                    paymentTransaction.setIdempotencyKey(resolvedIdempotencyKey);
                    paymentTransaction.setCorrelationId(trimToNull(correlationId));

                    PaymentTransaction saved = paymentTransactionRepository.save(paymentTransaction);
                    bankMessageProducer.publishTransactionEvent(saved, "refund.completed");
                    return new TransactionResult(new PaymentTransactionDTO(saved), true);
                });
    }

    @Transactional
    public PaymentTransactionDTO getPaymentTransaction(Long transactionID) {
        Objects.requireNonNull(transactionID, "transactionID must not be null");
        return paymentTransactionRepository.findById(transactionID)
                .map(PaymentTransactionDTO::new)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionID));
    }

    /**
     * Resolve the refund amount. If the caller supplies a positive amount use it,
     * otherwise fall back to the most recent purchase recorded for the order.
     */
    private Double resolveRefundAmount(UUID orderID, Double amount) {
        if (amount != null && amount > 0) {
            return amount;
        }

        return paymentTransactionRepository
                .findTopByOrderIDAndTypeOrderByTimeStampDesc(orderID, "Purchase")
                .map(PaymentTransaction::getAmount)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unable to determine refund amount for order " + orderID));
    }

    private static void validateOrder(UUID orderID) {
        if (orderID == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
    }

    private static void validateAmount(Double amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        return idempotencyKey.trim();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public record TransactionResult(PaymentTransactionDTO transaction, boolean createdNew) {}
}
