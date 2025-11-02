package com.comp5348.store.order.application.policy;

public class CircuitBreakerExecutionException extends RuntimeException {

    public CircuitBreakerExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
