package com.comp5348.store.order.exception;

/**
 * Exception thrown when an order state transition is invalid.
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(String message, Throwable cause) {
        super(message, cause);
    }
}

