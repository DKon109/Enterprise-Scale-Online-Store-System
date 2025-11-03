package com.comp5348.store.order.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

/**
 * Unit tests for Money value object.
 * Tests immutability, arithmetic operations, and currency handling.
 */
class MoneyTest {

    @Test
    void createMoneyFromBigDecimalAndCurrency() {
        BigDecimal amount = new BigDecimal("100.50");
        Money money = Money.of(amount, "AUD");

        assertEquals(new BigDecimal("100.50"), money.amount());
        assertEquals("AUD", money.currency());
    }

    @Test
    void createMoneyFromDouble() {
        Money money = Money.of(100.50);

        assertEquals(new BigDecimal("100.50"), money.amount());
        assertEquals("AUD", money.currency());
    }

    @Test
    void createMoneyThrowsExceptionIfAmountIsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> Money.of(null, "AUD"));
    }

    @Test
    void createMoneyThrowsExceptionIfCurrencyIsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> Money.of(new BigDecimal("100"), null));
    }

    @Test
    void createMoneyThrowsExceptionIfCurrencyIsBlank() {
        assertThrows(IllegalArgumentException.class,
            () -> Money.of(new BigDecimal("100"), "   "));
    }

    @Test
    void moneyNormalizesToTwoDecimalPlaces() {
        Money money = Money.of(new BigDecimal("100.555"), "AUD");

        assertEquals(new BigDecimal("100.56"), money.amount());
    }

    @Test
    void multiplyByIntegerFactor() {
        Money money = Money.of(10.00, "AUD");
        Money result = money.multiply(5);

        assertEquals(new BigDecimal("50.00"), result.amount());
        assertEquals("AUD", result.currency());
    }

    @Test
    void multiplyByBigDecimalFactor() {
        Money money = Money.of(10.00, "AUD");
        Money result = money.multiply(new BigDecimal("2.5"));

        assertEquals(new BigDecimal("25.00"), result.amount());
        assertEquals("AUD", result.currency());
    }

    @Test
    void multiplyReturnsNewInstance() {
        Money original = Money.of(10.00, "AUD");
        Money result = original.multiply(2);

        assertNotSame(original, result);
        assertEquals(new BigDecimal("10.00"), original.amount());
        assertEquals(new BigDecimal("20.00"), result.amount());
    }

    @Test
    void equalsBasedOnAmountAndCurrency() {
        Money money1 = Money.of(100.00, "AUD");
        Money money2 = Money.of(100.00, "AUD");

        assertEquals(money1, money2);
    }

    @Test
    void notEqualsForDifferentAmounts() {
        Money money1 = Money.of(100.00, "AUD");
        Money money2 = Money.of(200.00, "AUD");

        assertNotEquals(money1, money2);
    }

    @Test
    void notEqualsForDifferentCurrencies() {
        Money money1 = Money.of(100.00, "AUD");
        Money money2 = Money.of(100.00, "USD");

        assertNotEquals(money1, money2);
    }

    @Test
    void hashCodeConsistentWithEquals() {
        Money money1 = Money.of(100.00, "AUD");
        Money money2 = Money.of(100.00, "AUD");

        assertEquals(money1.hashCode(), money2.hashCode());
    }

    @Test
    void toStringFormatsCorrectly() {
        Money money = Money.of(100.50, "AUD");

        assertEquals("100.50 AUD", money.toString());
    }

    @Test
    void moneyIsImmutable() {
        Money money1 = Money.of(100.00, "AUD");
        Money money2 = money1.multiply(2);

        // Original should not be modified
        assertEquals(new BigDecimal("100.00"), money1.amount());
        assertEquals(new BigDecimal("200.00"), money2.amount());
    }
}

