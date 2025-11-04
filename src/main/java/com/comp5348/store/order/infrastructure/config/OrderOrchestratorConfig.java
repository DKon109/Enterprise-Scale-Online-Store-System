package com.comp5348.store.order.infrastructure.config;

import com.comp5348.store.order.application.policy.CircuitBreaker;
import com.comp5348.store.order.application.policy.RetryPolicy;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the Order Orchestrator and its resilience policies.
 *
 * Defines beans for:
 * - RetryPolicy: Exponential backoff for transient failures
 * - CircuitBreaker: Fail-fast protection for critical services
 */
@Configuration
public class OrderOrchestratorConfig {

    /**
     * Retry policy with exponential backoff.
     *
     * Schedule:
     * - Attempt 1: Immediate
     * - Attempt 2: After 200ms
     * - Attempt 3: After 500ms
     * - Attempt 4: After 1000ms
     *
     * Applied to: Bank authorization, Warehouse reservation, DeliveryCo shipment requests
     */
    @Bean
    public RetryPolicy retryPolicy() {
        Duration[] backoffSchedule = new Duration[]{
                Duration.ofMillis(200),
                Duration.ofMillis(500),
                Duration.ofMillis(1000)
        };
        return RetryPolicy.exponential(backoffSchedule);
    }

    /**
     * Circuit breaker for critical external services.
     *
     * Configuration:
     * - Failure threshold: 5 consecutive failures
     * - Open duration: 30 seconds
     * - State transitions: CLOSED → OPEN → HALF_OPEN → CLOSED
     *
     * Applied to: Bank service (payment authorization), DeliveryCo service (shipment requests)
     */
    @Bean
    public CircuitBreaker circuitBreaker() {
        return new CircuitBreaker(
                5,                          // failureThreshold: open after 5 failures
                Duration.ofSeconds(30)      // openDuration: stay open for 30 seconds
        );
    }

    /**
     * Logger for inter-service calls.
     *
     * Logs all external service invocations with:
     * - Order ID
     * - Correlation ID
     * - Service step name
     * - Attempt number
     * - Latency in milliseconds
     * - Outcome (SUCCESS, RETRYING, FAILED)
     */
    @Bean
    public InterServiceCallLogger interServiceCallLogger() {
        return new InterServiceCallLogger();
    }

    /**
     * RestTemplate bean for HTTP calls to external services.
     * Used by adapters to call Bank, Inventory, Delivery, and Email services.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(java.time.Duration.ofSeconds(5))
                .setReadTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }
}

