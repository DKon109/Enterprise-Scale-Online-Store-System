package com.comp5348.store.order.application.policy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class CircuitBreaker {

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int failureCount = 0;
    private Instant openedAt;

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        this(failureThreshold, openDuration, Clock.systemUTC());
    }

    public CircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public synchronized <T> T protect(CheckedSupplier<T> supplier) {
        if (state == State.OPEN) {
            if (!hasOpenWindowExpired()) {
                throw new CircuitBreakerOpenException("Circuit breaker open; calls blocked");
            }
            state = State.HALF_OPEN;
        }

        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (RuntimeException ex) {
            onFailure();
            throw ex;
        } catch (Exception ex) {
            onFailure();
            throw new CircuitBreakerExecutionException("Circuit breaker captured checked exception", ex);
        }
    }

    private boolean hasOpenWindowExpired() {
        if (openedAt == null) {
            return true;
        }
        Instant now = clock.instant();
        return now.isAfter(openedAt.plus(openDuration));
    }

    private void onSuccess() {
        failureCount = 0;
        state = State.CLOSED;
        openedAt = null;
    }

    private void onFailure() {
        failureCount++;
        if (failureCount >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
        }
    }

    public synchronized State getState() {
        if (state == State.OPEN && hasOpenWindowExpired()) {
            state = State.HALF_OPEN;
        }
        return state;
    }
}
