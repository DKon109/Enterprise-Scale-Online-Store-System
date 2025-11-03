package com.comp5348.store.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CustomerAlreadyExistsException extends RuntimeException {

    public CustomerAlreadyExistsException(String username) {
        super("Customer already exists with username: " + username);
    }
}
