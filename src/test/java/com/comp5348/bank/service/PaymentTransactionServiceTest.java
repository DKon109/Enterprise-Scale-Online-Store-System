package com.comp5348.bank.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.comp5348.bank.dto.PaymentTransactionDTO;
import com.comp5348.bank.model.PaymentTransaction;
import com.comp5348.bank.repository.PaymentTransactionRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @InjectMocks
    private PaymentTransactionService service;

    @Test
    void createPurchaseTransactionPersistsNewRecordWhenKeyNotSeen() {
        UUID orderId = UUID.randomUUID();
        Double amount = 120.50;
        String idempotencyKey = "  purchase-key-001  ";
        String correlationId = "corr-123";

        when(paymentTransactionRepository.findByIdempotencyKey("purchase-key-001"))
                .thenReturn(Optional.empty());

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        when(paymentTransactionRepository.save(captor.capture()))
                .thenAnswer(invocation -> {
                    PaymentTransaction entity = invocation.getArgument(0);
                    setField(entity, "id", 42L);
                    setField(entity, "version", 1);
                    return entity;
                });

        PaymentTransactionService.TransactionResult result = service.createPurchaseTransaction(
                orderId, amount, idempotencyKey, correlationId);

        assertTrue(result.createdNew());
        PaymentTransactionDTO dto = result.transaction();
        assertEquals(42L, dto.getId());
        assertEquals(amount, dto.getAmount());
        assertEquals(orderId, dto.getOrderId());
        assertEquals("purchase-key-001", dto.getIdempotencyKey());
        assertEquals("corr-123", dto.getCorrelationId());
        assertEquals("Confirmed", dto.getStatus());
        assertEquals("Purchase", dto.getType());

        PaymentTransaction saved = captor.getValue();
        assertEquals("purchase-key-001", getField(saved, "idempotencyKey"));
        assertEquals("corr-123", getField(saved, "correlationId"));
        assertEquals(orderId, saved.getOrderID());
        assertEquals("Purchase", saved.getType());
        assertEquals("Confirmed", saved.getStatus());

        verify(paymentTransactionRepository, times(1)).save(any(PaymentTransaction.class));
    }

    @Test
    void createPurchaseTransactionReturnsExistingForDuplicateKey() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction existing = new PaymentTransaction(75.0, LocalDateTime.now(), "Purchase", "Confirmed", orderId);
        existing.setIdempotencyKey("dup-key");
        existing.setCorrelationId("existing-correlation");
        setField(existing, "id", 10L);

        when(paymentTransactionRepository.findByIdempotencyKey("dup-key"))
                .thenReturn(Optional.of(existing));

        PaymentTransactionService.TransactionResult result = service.createPurchaseTransaction(
                orderId, 75.0, "dup-key", "ignored");

        assertFalse(result.createdNew());
        PaymentTransactionDTO dto = result.transaction();
        assertEquals(existing.getId(), dto.getId());
        assertEquals(existing.getAmount(), dto.getAmount());
        assertEquals(existing.getIdempotencyKey(), dto.getIdempotencyKey());
        assertEquals("existing-correlation", dto.getCorrelationId());

        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void createPurchaseTransactionRejectsInvalidAmount() {
        UUID orderId = UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createPurchaseTransaction(orderId, 0.0, "key", null));

        assertEquals("Amount must be greater than zero", ex.getMessage());
        verifyNoInteractions(paymentTransactionRepository);
    }

    @Test
    void createPurchaseTransactionRejectsMissingIdempotencyKey() {
        UUID orderId = UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createPurchaseTransaction(orderId, 50.0, "   ", null));

        assertEquals("Idempotency key is required", ex.getMessage());
        verifyNoInteractions(paymentTransactionRepository);
    }

    @Test
    void createRefundTransactionUsesFallbackAmountWhenNotProvided() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction lastPurchase = new PaymentTransaction(88.0, LocalDateTime.now().minusMinutes(5), "Purchase", "Confirmed", orderId);

        when(paymentTransactionRepository.findByIdempotencyKey("refund-key"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findTopByOrderIDAndTypeOrderByTimeStampDesc(orderId, "Purchase"))
                .thenReturn(Optional.of(lastPurchase));

        ArgumentCaptor<PaymentTransaction> refundCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        when(paymentTransactionRepository.save(refundCaptor.capture()))
                .thenAnswer(invocation -> {
                    PaymentTransaction entity = invocation.getArgument(0);
                    setField(entity, "id", 99L);
                    return entity;
                });

        PaymentTransactionService.TransactionResult result = service.createRefundTransaction(
                orderId, null, "refund-key", "refund-corr");

        assertTrue(result.createdNew());
        PaymentTransactionDTO dto = result.transaction();
        assertEquals(88.0, dto.getAmount());
        assertEquals("Refund", dto.getType());
        assertEquals("Confirmed", dto.getStatus());
        assertEquals("refund-key", dto.getIdempotencyKey());
        assertEquals("refund-corr", dto.getCorrelationId());

        PaymentTransaction persisted = refundCaptor.getValue();
        assertEquals("Refund", persisted.getType());
        assertEquals(88.0, persisted.getAmount());
        assertEquals("Confirmed", persisted.getStatus());
    }

    @Test
    void createRefundTransactionReturnsExistingForDuplicateKey() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction existing = new PaymentTransaction(45.0, LocalDateTime.now(), "Refund", "Confirmed", orderId);
        existing.setIdempotencyKey("refund-dup");
        existing.setCorrelationId(null);

        when(paymentTransactionRepository.findByIdempotencyKey("refund-dup"))
                .thenReturn(Optional.of(existing));

        PaymentTransactionService.TransactionResult result = service.createRefundTransaction(
                orderId, 45.0, "refund-dup", "ignored");

        assertFalse(result.createdNew());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void createRefundTransactionThrowsWhenFallbackMissing() {
        UUID orderId = UUID.randomUUID();

        when(paymentTransactionRepository.findByIdempotencyKey("refund-key"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findTopByOrderIDAndTypeOrderByTimeStampDesc(orderId, "Purchase"))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRefundTransaction(orderId, null, "refund-key", null));

        assertTrue(ex.getMessage().contains("Unable to determine refund amount"));
    }

    @Test
    void getPaymentTransactionReturnsDtoWhenFound() {
        PaymentTransaction entity = new PaymentTransaction(60.0, LocalDateTime.now(), "Purchase", "Confirmed", UUID.randomUUID());
        entity.setIdempotencyKey("id-1");
        entity.setCorrelationId("corr-id");
        setField(entity, "id", 7L);

        when(paymentTransactionRepository.findById(7L)).thenReturn(Optional.of(entity));

        PaymentTransactionDTO dto = service.getPaymentTransaction(7L);

        assertEquals(7L, dto.getId());
        assertEquals("Purchase", dto.getType());
        assertEquals("Confirmed", dto.getStatus());
        assertEquals("id-1", dto.getIdempotencyKey());
        assertEquals("corr-id", dto.getCorrelationId());
    }

    @Test
    void getPaymentTransactionThrowsWhenNotFound() {
        when(paymentTransactionRepository.findById(55L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.getPaymentTransaction(55L));

        assertEquals("Transaction not found: 55", ex.getMessage());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = PaymentTransaction.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (type == long.class && value instanceof Long longValue) {
                field.setLong(target, longValue);
            } else if (type == int.class && value instanceof Integer intValue) {
                field.setInt(target, intValue);
            } else {
                field.set(target, value);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to set field '" + fieldName + "'", e);
        }
    }

    private static Object getField(Object target, String fieldName) {
        try {
            Field field = PaymentTransaction.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to access field '" + fieldName + "'", e);
        }
    }
}
