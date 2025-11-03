package com.comp5348.store.order.application.policy;

import java.time.Duration;
import java.util.Optional;

public class RetryPolicy {

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;

        Sleeper NOOP = duration -> { };
    }

    public interface AttemptListener {

        void onSuccess(int attemptNumber);

        void onFailure(int attemptNumber, Exception error, boolean willRetry);

        AttemptListener NOOP = new AttemptListener() {
            @Override
            public void onSuccess(int attemptNumber) { }

            @Override
            public void onFailure(int attemptNumber, Exception error, boolean willRetry) { }
        };
    }

    private final Duration[] backoffSchedule;
    private final Sleeper sleeper;

    public RetryPolicy(Duration[] backoffSchedule, Sleeper sleeper) {
        this.backoffSchedule = backoffSchedule;
        this.sleeper = sleeper;
    }

    public static RetryPolicy exponential(Duration... schedule) {
        return new RetryPolicy(schedule, Sleeper.NOOP);
    }

    public <T> Optional<T> execute(CheckedSupplier<T> supplier) {
        return execute(supplier, AttemptListener.NOOP);
    }

    public <T> Optional<T> execute(CheckedSupplier<T> supplier, AttemptListener listener) {
        int attempts = backoffSchedule.length + 1;
        Exception lastError = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                T result = supplier.get();
                listener.onSuccess(attempt + 1);
                return Optional.ofNullable(result);
            } catch (Exception ex) {
                lastError = ex;
                boolean willRetry = attempt < attempts - 1;
                listener.onFailure(attempt + 1, ex, willRetry);
                if (attempt == attempts - 1) {
                    break;
                }
                Duration delay = backoffSchedule[attempt];
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Retry interrupted", interrupted);
                }
            }
        }
        if (lastError != null) {
            throw new IllegalStateException("Retry attempts exhausted", lastError);
        }
        return Optional.empty();
    }
}
