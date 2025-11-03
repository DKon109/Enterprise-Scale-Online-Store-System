package com.comp5348.store.customer.service;

import com.comp5348.store.customer.exception.CustomerAlreadyExistsException;
import com.comp5348.store.customer.exception.InvalidPasswordException;
import com.comp5348.store.customer.model.Customer;
import com.comp5348.store.customer.repository.CustomerRepository;
import java.util.UUID;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Customer registerCustomer(String username, String email, String rawPassword) {
        if (customerRepository.existsByUsername(username)) {
            throw new CustomerAlreadyExistsException(username);
        }
        Customer customer =
            new Customer(UUID.randomUUID(), username, passwordEncoder.encode(rawPassword), email);
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getByUsername(String username) {
        return customerRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Customer not found: " + username));
    }

    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return customerRepository.existsByUsername(username);
    }

    @Transactional(readOnly = true)
    public void validatePassword(String rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new InvalidPasswordException();
        }
    }
}
