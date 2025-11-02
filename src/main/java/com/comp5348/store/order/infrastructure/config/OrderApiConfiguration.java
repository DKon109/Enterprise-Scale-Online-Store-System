package com.comp5348.store.order.infrastructure.config;

import com.comp5348.store.order.application.policy.CircuitBreaker;
import com.comp5348.store.order.application.policy.RetryPolicy;
import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import com.comp5348.store.order.application.service.OrderQueryService;
import com.comp5348.store.order.application.service.OrderOrchestrator;
import com.comp5348.store.order.application.support.TransactionTemplate;
import com.comp5348.store.order.domain.repository.OrderRepository;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import com.comp5348.store.order.infrastructure.persistence.PostgresConnectionProvider;
import com.comp5348.store.order.infrastructure.persistence.JdbcTransactionTemplate;
import com.comp5348.store.order.infrastructure.persistence.PostgresOrderRepository;
import com.comp5348.store.order.presentation.OrderController;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-independent order API components for Spring.
 */
@Configuration
public class OrderApiConfiguration {

    @Bean
    public PostgresConnectionProvider orderConnectionProvider(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {
        return new PostgresConnectionProvider(jdbcUrl, username, password);
    }

    @Bean
    public OrderRepository orderRepository(PostgresConnectionProvider connectionProvider) {
        return new PostgresOrderRepository(connectionProvider);
    }



    @Bean
    public TransactionTemplate transactionTemplate(PostgresConnectionProvider connectionProvider) {
        return new JdbcTransactionTemplate(connectionProvider);
    }

    @Bean
    public RetryPolicy retryPolicy() {
        return RetryPolicy.exponential(
                Duration.ofMillis(200),
                Duration.ofMillis(500),
                Duration.ofMillis(1000));
    }

    @Bean
    public CircuitBreaker circuitBreaker() {
        return new CircuitBreaker(3, Duration.ofSeconds(5));
    }

    @Bean
    public InterServiceCallLogger interServiceCallLogger() {
        return new InterServiceCallLogger();
    }

    @Bean
    public OrderOrchestrator orderOrchestrator(
            OrderRepository orders,
            InventoryServicePort inventory,
            PaymentServicePort payments,
            ShippingServicePort shipping,
            NotificationServicePort notifications,
            TransactionTemplate transactions,
            RetryPolicy retryPolicy,
            CircuitBreaker circuitBreaker,
            InterServiceCallLogger callLogger) {
        return new OrderOrchestrator(
                orders,
                inventory,
                payments,
                shipping,
                notifications,
                transactions,
                retryPolicy,
                circuitBreaker,
                callLogger);
    }

    @Bean
    public OrderQueryService orderQueryService(OrderRepository orders) {
        return new OrderQueryService(orders);
    }

    @Bean
    public OrderController orderController(OrderOrchestrator orchestrator, OrderQueryService queries) {
        return new OrderController(orchestrator, queries);
    }
}
