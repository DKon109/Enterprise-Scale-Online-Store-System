package com.comp5348.store.customer.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerTest {

    @Test
    void constructorSetsAllFields() {
        UUID id = UUID.randomUUID();
        String username = "john";
        String passwordHash = "hashedPassword123";
        String email = "john@example.com";

        Customer customer = new Customer(id, username, passwordHash, email);

        assertEquals(id, customer.getId());
        assertEquals(username, customer.getUsername());
        assertEquals(passwordHash, customer.getPasswordHash());
        assertEquals(email, customer.getEmail());
    }

    @Test
    void constructorThrowsExceptionIfIdIsNull() {
        assertThrows(
            NullPointerException.class,
            () -> new Customer(null, "john", "hashedPassword", "john@example.com"));
    }

    @Test
    void constructorThrowsExceptionIfUsernameIsNull() {
        UUID id = UUID.randomUUID();
        assertThrows(
            NullPointerException.class,
            () -> new Customer(id, null, "hashedPassword", "john@example.com"));
    }

    @Test
    void constructorThrowsExceptionIfPasswordHashIsNull() {
        UUID id = UUID.randomUUID();
        assertThrows(
            NullPointerException.class,
            () -> new Customer(id, "john", null, "john@example.com"));
    }

    @Test
    void constructorThrowsExceptionIfEmailIsNull() {
        UUID id = UUID.randomUUID();
        assertThrows(
            NullPointerException.class,
            () -> new Customer(id, "john", "hashedPassword", null));
    }

    @Test
    void setPasswordHashUpdatesPasswordHash() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "john", "oldHash", "john@example.com");
        String newHash = "newHash123";

        customer.setPasswordHash(newHash);

        assertEquals(newHash, customer.getPasswordHash());
    }

    @Test
    void setPasswordHashThrowsExceptionIfNull() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "john", "hashedPassword", "john@example.com");

        assertThrows(NullPointerException.class, () -> customer.setPasswordHash(null));
    }

    @Test
    void setEmailUpdatesEmail() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "john", "hashedPassword", "john@example.com");
        String newEmail = "newemail@example.com";

        customer.setEmail(newEmail);

        assertEquals(newEmail, customer.getEmail());
    }

    @Test
    void setEmailThrowsExceptionIfNull() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "john", "hashedPassword", "john@example.com");

        assertThrows(NullPointerException.class, () -> customer.setEmail(null));
    }

    @Test
    void usernameIsImmutable() {
        UUID id = UUID.randomUUID();
        String username = "john";
        Customer customer = new Customer(id, username, "hashedPassword", "john@example.com");

        // Username should not have a setter, verify it's immutable
        assertEquals(username, customer.getUsername());
        // No way to change username after construction
    }

    @Test
    void idIsImmutable() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "john", "hashedPassword", "john@example.com");

        // ID should not have a setter, verify it's immutable
        assertEquals(id, customer.getId());
        // No way to change id after construction
    }

    @Test
    void multipleCustomersWithDifferentIdsAreNotEqual() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Customer customer1 = new Customer(id1, "john", "hash1", "john@example.com");
        Customer customer2 = new Customer(id2, "john", "hash1", "john@example.com");

        assertNotEquals(customer1.getId(), customer2.getId());
    }

    @Test
    void customerCanBeCreatedWithValidData() {
        UUID id = UUID.randomUUID();
        String username = "testuser";
        String passwordHash = "$2a$10$hashedpassword";
        String email = "test@example.com";

        Customer customer = new Customer(id, username, passwordHash, email);

        assertNotNull(customer);
        assertEquals(id, customer.getId());
        assertEquals(username, customer.getUsername());
        assertEquals(passwordHash, customer.getPasswordHash());
        assertEquals(email, customer.getEmail());
    }
}

