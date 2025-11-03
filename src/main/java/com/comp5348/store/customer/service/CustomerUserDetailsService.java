package com.comp5348.store.customer.service;

import com.comp5348.store.customer.model.Customer;
import com.comp5348.store.customer.repository.CustomerRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomerUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Customer customer =
            customerRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Customer not found: " + username));

        return User.withUsername(customer.getUsername())
            .password(customer.getPasswordHash())
            .roles("USER")
            .build();
    }
}
