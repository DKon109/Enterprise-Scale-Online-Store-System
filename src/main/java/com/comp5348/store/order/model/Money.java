package com.comp5348.store.order.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money Value Object - Immutable representation of a monetary amount.
 *
 * Uses BigDecimal for precise financial calculations (no floating-point errors).
 * Currency-aware and immutable.
 */
public final class Money {

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        // Normalize to 2 decimal places (cents)
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    /**
     * Create Money from BigDecimal and currency.
     */
    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    /**
     * Create Money from double and currency.
     * Converts double to BigDecimal to avoid floating-point precision issues.
     */
    public static Money of(double amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    /**
     * Create Money from double (convenience method, defaults to AUD).
     */
    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount), "AUD");
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    /**
     * Multiply this money by an integer factor.
     */
    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    /**
     * Multiply this money by a BigDecimal factor.
     */
    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money money)) {
            return false;
        }
        return amount.equals(money.amount) && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
}

