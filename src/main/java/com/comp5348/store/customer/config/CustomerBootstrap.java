package com.comp5348.store.customer.config;

import com.comp5348.store.customer.service.CustomerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerBootstrap {

    @Bean
    CommandLineRunner demoCustomerInitializer(CustomerService customerService) {
        return args -> {
            if (!customerService.existsByUsername("customer")) {
                customerService.registerCustomer("customer", "customer@example.com", "COMP5348");
            }
        };
    }
}
