package com.comp5348.bank.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.comp5348.bank.model.PaymentTransaction;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class PaymentTransactionRepositoryTest {

    @Autowired
    private PaymentTransactionRepository repository;

    @Test
    void saveAndRetrieveTransaction() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction(150.0, LocalDateTime.now(), "Purchase", "Confirmed", orderId);
        transaction.setIdempotencyKey("repo-key-1");
        transaction.setCorrelationId("corr-repo-1");

        PaymentTransaction saved = repository.save(transaction);

        Optional<PaymentTransaction> retrieved = repository.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("repo-key-1", retrieved.get().getIdempotencyKey());
        assertEquals("corr-repo-1", retrieved.get().getCorrelationId());
    }

    @Test
    void uniqueConstraintOnIdempotencyKey() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction first = new PaymentTransaction(80.0, LocalDateTime.now(), "Purchase", "Confirmed", orderId);
        first.setIdempotencyKey("unique-key");
        repository.save(first);

        PaymentTransaction duplicate = new PaymentTransaction(80.0, LocalDateTime.now(), "Purchase", "Confirmed", orderId);
        duplicate.setIdempotencyKey("unique-key");

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(duplicate);
        });
    }

    @Test
    void findTopByOrderIdAndTypeReturnsLatestPurchase() {
        UUID orderId = UUID.randomUUID();

        PaymentTransaction earlier = new PaymentTransaction(100.0, LocalDateTime.now().minusMinutes(10), "Purchase", "Confirmed", orderId);
        earlier.setIdempotencyKey("lookup-1");
        repository.save(earlier);

        PaymentTransaction later = new PaymentTransaction(120.0, LocalDateTime.now(), "Purchase", "Confirmed", orderId);
        later.setIdempotencyKey("lookup-2");
        repository.save(later);

        Optional<PaymentTransaction> result = repository.findTopByOrderIDAndTypeOrderByTimeStampDesc(orderId, "Purchase");
        assertTrue(result.isPresent());
        assertEquals("lookup-2", result.get().getIdempotencyKey());
    }

    @Test
    void versionFieldIncrementsOnUpdate() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction(60.0, LocalDateTime.now(), "Purchase", "Pending", orderId);
        transaction.setIdempotencyKey("version-key");

        PaymentTransaction saved = repository.saveAndFlush(transaction);
        int initialVersion = saved.getVersion();

        saved.setStatus("Confirmed");
        PaymentTransaction updated = repository.saveAndFlush(saved);

        assertEquals(initialVersion + 1, updated.getVersion());
    }

    @Test
    void saveRefundTransactionPersistsWithCorrelation() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction refund = new PaymentTransaction(45.0, LocalDateTime.now(), "Refund", "Confirmed", orderId);
        refund.setIdempotencyKey("refund-key");
        refund.setCorrelationId("corr-refund");

        PaymentTransaction persisted = repository.save(refund);

        Optional<PaymentTransaction> retrieved = repository.findById(persisted.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("Refund", retrieved.get().getType());
        assertEquals("corr-refund", retrieved.get().getCorrelationId());
    }
}
