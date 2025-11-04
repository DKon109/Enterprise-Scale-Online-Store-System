package com.comp5348.store.customer.controller;

import com.comp5348.config.JwtTokenProvider;
import com.comp5348.store.customer.controller.dto.CustomerResponse;
import com.comp5348.store.customer.controller.dto.LoginRequest;
import com.comp5348.store.customer.controller.dto.LoginResponse;
import com.comp5348.store.customer.controller.dto.RegisterCustomerRequest;
import com.comp5348.store.customer.model.Customer;
import com.comp5348.store.customer.service.CustomerService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final JwtTokenProvider jwtTokenProvider;

    public CustomerController(
            CustomerService customerService,
            JwtTokenProvider jwtTokenProvider) {
        this.customerService = customerService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterCustomerRequest request) {
        Customer customer =
            customerService.registerCustomer(request.getUsername(), request.getEmail(), request.getPassword());
        return CustomerResponse.from(customer);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Customer customer = customerService.getByUsername(request.getUsername());
        customerService.validatePassword(request.getPassword(), customer.getPasswordHash());
        String token = jwtTokenProvider.generateToken(customer.getUsername());
        return new LoginResponse(token, customer.getUsername());
    }

    @GetMapping("/me")
    public CustomerResponse currentCustomer(Principal principal) {
        Customer customer = customerService.getByUsername(principal.getName());
        return CustomerResponse.from(customer);
    }
}
