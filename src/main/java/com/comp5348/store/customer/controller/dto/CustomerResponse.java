package com.comp5348.store.customer.controller.dto;

import com.comp5348.store.customer.model.Customer;
import java.time.Instant;
import java.util.UUID;

public class CustomerResponse {

    private final UUID id;
    private final String username;
    private final String email;
    private final Instant createdAt;

    public CustomerResponse(UUID id, String username, String email, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
            customer.getId(), customer.getUsername(), customer.getEmail(), customer.getCreatedAt());
    }
}
