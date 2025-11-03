package com.comp5348.store.order.config;

import com.comp5348.store.order.application.policy.CircuitBreaker;
import com.comp5348.store.order.application.policy.RetryPolicy;
import com.comp5348.store.order.application.support.TransactionTemplate;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import com.comp5348.store.order.infrastructure.support.SpringTransactionTemplate;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring configuration for the Order Module.
 *
 * <p>This configuration class sets up the resilience patterns, logging, and transaction
 * management for the order orchestration layer. It provides beans for:
 *
 * <ul>
 *   <li><b>RetryPolicy</b>: Exponential backoff retry strategy (200ms → 500ms → 1000ms)
 *   <li><b>CircuitBreaker</b>: Fail-fast pattern to prevent cascading failures
 *   <li><b>InterServiceCallLogger</b>: Observability for inter-service calls
 *   <li><b>TransactionTemplate</b>: Framework-agnostic transaction handling
 * </ul>
 *
 * <p><b>Architecture Pattern:</b> Hexagonal Architecture (Ports & Adapters)
 *
 * <p><b>Resilience Strategy:</b>
 * <ul>
 *   <li>Retry: 3 attempts with exponential backoff
 *   <li>Circuit Breaker: Opens after 3 failures, resets after 5 seconds
 *   <li>Logging: All inter-service calls logged with correlation IDs
 * </ul>
 *
 * @see RetryPolicy
 * @see CircuitBreaker
 * @see InterServiceCallLogger
 * @see TransactionTemplate
 */
@Configuration
public class OrderModuleConfiguration {

    /**
     * Configures the retry policy for inter-service calls.
     *
     * <p><b>Strategy:</b> Exponential backoff with three attempts
     * <ul>
     *   <li>Attempt 1: Wait 200ms before retry
     *   <li>Attempt 2: Wait 500ms before retry
     *   <li>Attempt 3: Wait 1000ms before retry
     * </ul>
     *
     * <p><b>Use Case:</b> Handles transient failures (network timeouts, temporary service
     * unavailability) without overwhelming the downstream service.
     *
     * @return RetryPolicy configured with exponential backoff
     */
    @Bean
    public RetryPolicy orderRetryPolicy() {
        return RetryPolicy.exponential(
                Duration.ofMillis(200),
                Duration.ofMillis(500),
                Duration.ofMillis(1000));
    }

    /**
     * Configures the circuit breaker for payment service resilience.
     *
     * <p><b>Strategy:</b> Fail-fast pattern
     * <ul>
     *   <li>Threshold: Opens after 3 consecutive failures
     *   <li>Timeout: Resets to half-open state after 5 seconds
     *   <li>Benefit: Prevents cascading failures across service boundaries
     * </ul>
     *
     * <p><b>Use Case:</b> If the payment service is down, fail immediately instead of
     * retrying and blocking the order orchestration.
     *
     * @return CircuitBreaker configured with 3-failure threshold and 5-second timeout
     */
    @Bean
    public CircuitBreaker orderCircuitBreaker() {
        return new CircuitBreaker(3, Duration.ofSeconds(5));
    }

    /**
     * Configures the logger for inter-service calls.
     *
     * <p><b>Observability:</b> Logs all inter-service calls with:
     * <ul>
     *   <li>Order ID
     *   <li>Correlation ID (for distributed tracing)
     *   <li>Service name and operation
     *   <li>Attempt number and latency
     *   <li>Success/failure status
     * </ul>
     *
     * <p><b>Use Case:</b> Enables debugging and monitoring of the saga workflow across
     * multiple services.
     *
     * @return InterServiceCallLogger instance
     */
    @Bean
    public InterServiceCallLogger interServiceCallLogger() {
        return new InterServiceCallLogger();
    }

    /**
     * Configures the transaction template for framework-agnostic transaction handling.
     *
     * <p><b>Abstraction:</b> Decouples the application layer from Spring-specific
     * transaction management, enabling easier testing and potential framework migration.
     *
     * <p><b>Use Case:</b> Wraps Spring's PlatformTransactionManager in a framework-agnostic
     * interface for use in the OrderOrchestrator.
     *
     * @param transactionManager Spring's transaction manager
     * @return TransactionTemplate adapter for Spring
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new SpringTransactionTemplate(transactionManager);
    }
}
