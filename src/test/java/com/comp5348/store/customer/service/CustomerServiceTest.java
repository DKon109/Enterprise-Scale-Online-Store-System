package com.comp5348.store.customer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.comp5348.store.customer.exception.CustomerAlreadyExistsException;
import com.comp5348.store.customer.exception.InvalidPasswordException;
import com.comp5348.store.customer.model.Customer;
import com.comp5348.store.customer.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerService service;

    @Test
    void registerCustomerSuccessfully() {
        String username = "john";
        String email = "john@example.com";
        String rawPassword = "password123";
        String encodedPassword = "hashedPassword";
        UUID customerId = UUID.randomUUID();

        when(customerRepository.existsByUsername(username)).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        Customer savedCustomer = new Customer(customerId, username, encodedPassword, email);
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        Customer result = service.registerCustomer(username, email, rawPassword);

        assertEquals(username, result.getUsername());
        assertEquals(email, result.getEmail());
        assertEquals(encodedPassword, result.getPasswordHash());
        verify(customerRepository).existsByUsername(username);
        verify(passwordEncoder).encode(rawPassword);
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void registerCustomerThrowsExceptionIfUsernameExists() {
        String username = "john";
        String email = "john@example.com";
        String rawPassword = "password123";

        when(customerRepository.existsByUsername(username)).thenReturn(true);

        assertThrows(
            CustomerAlreadyExistsException.class,
            () -> service.registerCustomer(username, email, rawPassword));

        verify(customerRepository).existsByUsername(username);
        verify(passwordEncoder, never()).encode(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void getByUsernameReturnsCustomer() {
        String username = "john";
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, username, "hashedPassword", "john@example.com");

        when(customerRepository.findByUsername(username)).thenReturn(Optional.of(customer));

        Customer result = service.getByUsername(username);

        assertEquals(username, result.getUsername());
        assertEquals(customerId, result.getId());
        verify(customerRepository).findByUsername(username);
    }

    @Test
    void getByUsernameThrowsExceptionIfNotFound() {
        String username = "nonexistent";

        when(customerRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.getByUsername(username));

        verify(customerRepository).findByUsername(username);
    }

    @Test
    void existsByUsernameReturnsTrueIfExists() {
        String username = "john";

        when(customerRepository.existsByUsername(username)).thenReturn(true);

        boolean result = service.existsByUsername(username);

        assertTrue(result);
        verify(customerRepository).existsByUsername(username);
    }

    @Test
    void existsByUsernameReturnsFalseIfNotExists() {
        String username = "nonexistent";

        when(customerRepository.existsByUsername(username)).thenReturn(false);

        boolean result = service.existsByUsername(username);

        assertFalse(result);
        verify(customerRepository).existsByUsername(username);
    }

    @Test
    void validatePasswordSucceedsWithCorrectPassword() {
        String rawPassword = "password123";
        String encodedPassword = "hashedPassword";

        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        // Should not throw exception
        assertDoesNotThrow(() -> service.validatePassword(rawPassword, encodedPassword));

        verify(passwordEncoder).matches(rawPassword, encodedPassword);
    }

    @Test
    void validatePasswordThrowsExceptionWithWrongPassword() {
        String rawPassword = "wrongPassword";
        String encodedPassword = "hashedPassword";

        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false);

        assertThrows(
            InvalidPasswordException.class,
            () -> service.validatePassword(rawPassword, encodedPassword));

        verify(passwordEncoder).matches(rawPassword, encodedPassword);
    }
}

