package com.comp5348.store.order.infrastructure.config;

import com.comp5348.store.order.application.event.OutboxPublisher;
import com.comp5348.store.order.application.policy.CircuitBreaker;
import com.comp5348.store.order.application.policy.RetryPolicy;
import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import com.comp5348.store.order.application.service.OrderQueryService;
import com.comp5348.store.order.application.service.OrderOrchestrator;
import com.comp5348.store.order.application.support.TransactionTemplate;
import com.comp5348.store.order.domain.repository.OrderEventRepository;
import com.comp5348.store.order.domain.repository.OrderRepository;
import com.comp5348.store.order.domain.repository.OrderSagaStateRepository;
import com.comp5348.store.order.domain.repository.OutboxEventRepository;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import com.comp5348.store.order.infrastructure.outbox.PersistentOutboxPublisher;
import com.comp5348.store.order.infrastructure.outbox.PostgresOutboxEventRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresConnectionProvider;
import com.comp5348.store.order.infrastructure.persistence.JdbcTransactionTemplate;
import com.comp5348.store.order.infrastructure.persistence.PostgresOrderEventRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresOrderRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresSagaStateRepository;
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
    public OrderEventRepository orderEventRepository(PostgresConnectionProvider connectionProvider) {
        return new PostgresOrderEventRepository(connectionProvider);
    }

    @Bean
    public OrderSagaStateRepository orderSagaStateRepository(PostgresConnectionProvider connectionProvider) {
        return new PostgresSagaStateRepository(connectionProvider);
    }

    @Bean
    public OutboxEventRepository outboxEventRepository(PostgresConnectionProvider connectionProvider) {
        return new PostgresOutboxEventRepository(connectionProvider);
    }

    @Bean
    public OutboxPublisher outboxPublisher(OutboxEventRepository repository) {
        return new PersistentOutboxPublisher(repository);
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
            OutboxPublisher outboxPublisher,
            OrderSagaStateRepository sagaStates,
            OrderEventRepository events,
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
                outboxPublisher,
                sagaStates,
                events,
                callLogger);
    }

    @Bean
    public OrderQueryService orderQueryService(OrderRepository orders, OrderEventRepository events) {
        return new OrderQueryService(orders, events);
    }

    @Bean
    public OrderController orderController(OrderOrchestrator orchestrator, OrderQueryService queries) {
        return new OrderController(orchestrator, queries);
    }
}
