package com.comp5348.store.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException() {
        super("Invalid username or password");
    }
}

