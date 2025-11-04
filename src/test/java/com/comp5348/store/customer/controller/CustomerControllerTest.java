package com.comp5348.store.customer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.comp5348.config.JwtTokenProvider;
import com.comp5348.store.customer.controller.dto.CustomerResponse;
import com.comp5348.store.customer.controller.dto.LoginRequest;
import com.comp5348.store.customer.controller.dto.LoginResponse;
import com.comp5348.store.customer.controller.dto.RegisterCustomerRequest;
import com.comp5348.store.customer.exception.CustomerAlreadyExistsException;
import com.comp5348.store.customer.exception.InvalidPasswordException;
import com.comp5348.store.customer.model.Customer;
import com.comp5348.store.customer.service.CustomerService;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerController controller;

    @Test
    void registerReturnsCustomerResponseWithCreatedStatus() {
        RegisterCustomerRequest request = new RegisterCustomerRequest();
        request.setUsername("john");
        request.setEmail("john@example.com");
        request.setPassword("password123");

        UUID customerId = UUID.randomUUID();
        Customer savedCustomer = new Customer(customerId, "john", "hashedPassword", "john@example.com");

        when(customerService.registerCustomer("john", "john@example.com", "password123"))
            .thenReturn(savedCustomer);

        CustomerResponse response = controller.register(request);

        assertEquals("john", response.getUsername());
        assertEquals("john@example.com", response.getEmail());
        assertEquals(customerId, response.getId());
        verify(customerService).registerCustomer("john", "john@example.com", "password123");
    }

    @Test
    void registerThrowsExceptionIfUsernameExists() {
        RegisterCustomerRequest request = new RegisterCustomerRequest();
        request.setUsername("john");
        request.setEmail("john@example.com");
        request.setPassword("password123");

        when(customerService.registerCustomer("john", "john@example.com", "password123"))
            .thenThrow(new CustomerAlreadyExistsException("john"));

        try {
            controller.register(request);
        } catch (CustomerAlreadyExistsException e) {
            assertEquals("Customer already exists with username: john", e.getMessage());
        }

        verify(customerService).registerCustomer("john", "john@example.com", "password123");
    }

    @Test
    void currentCustomerReturnsAuthenticatedCustomer() {
        String username = "john";
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, username, "hashedPassword", "john@example.com");

        Principal principal = () -> username;

        when(customerService.getByUsername(username)).thenReturn(customer);

        CustomerResponse response = controller.currentCustomer(principal);

        assertEquals(username, response.getUsername());
        assertEquals("john@example.com", response.getEmail());
        assertEquals(customerId, response.getId());
        verify(customerService).getByUsername(username);
    }

    @Test
    void currentCustomerThrowsExceptionIfNotFound() {
        String username = "nonexistent";
        Principal principal = () -> username;

        when(customerService.getByUsername(username))
            .thenThrow(new UsernameNotFoundException("Customer not found: " + username));

        try {
            controller.currentCustomer(principal);
        } catch (UsernameNotFoundException e) {
            assertEquals("Customer not found: nonexistent", e.getMessage());
        }

        verify(customerService).getByUsername(username);
    }

    @Test
    void registerCallsServiceWithCorrectParameters() {
        RegisterCustomerRequest request = new RegisterCustomerRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("securePassword");

        UUID customerId = UUID.randomUUID();
        Customer savedCustomer = new Customer(customerId, "alice", "hashedPassword", "alice@example.com");

        when(customerService.registerCustomer("alice", "alice@example.com", "securePassword"))
            .thenReturn(savedCustomer);

        controller.register(request);

        verify(customerService).registerCustomer("alice", "alice@example.com", "securePassword");
    }

    @Test
    void currentCustomerExtractsUsernameFromPrincipal() {
        String username = "bob";
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, username, "hashedPassword", "bob@example.com");

        Principal principal = () -> username;

        when(customerService.getByUsername(username)).thenReturn(customer);

        controller.currentCustomer(principal);

        verify(customerService).getByUsername(username);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        String username = "john";
        String password = "password123";
        String hashedPassword = "$2a$10$hashedPassword";
        String token = "jwt.token.here";

        LoginRequest request = new LoginRequest(username, password);
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, username, hashedPassword, "john@example.com");

        when(customerService.getByUsername(username)).thenReturn(customer);
        when(jwtTokenProvider.generateToken(username)).thenReturn(token);

        LoginResponse response = controller.login(request);

        assertEquals(token, response.getToken());
        assertEquals(username, response.getUsername());
        assertEquals("Bearer", response.getType());
        verify(customerService).getByUsername(username);
        verify(customerService).validatePassword(password, hashedPassword);
        verify(jwtTokenProvider).generateToken(username);
    }

    @Test
    void loginThrowsExceptionForInvalidPassword() {
        String username = "john";
        String password = "wrongPassword";
        String hashedPassword = "$2a$10$hashedPassword";

        LoginRequest request = new LoginRequest(username, password);
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, username, hashedPassword, "john@example.com");

        when(customerService.getByUsername(username)).thenReturn(customer);
        doThrow(new InvalidPasswordException()).when(customerService).validatePassword(password, hashedPassword);

        assertThrows(InvalidPasswordException.class, () -> controller.login(request));

        verify(customerService).getByUsername(username);
        verify(customerService).validatePassword(password, hashedPassword);
    }
}

