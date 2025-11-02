package com.comp5348.store.order.infrastructure.config;

import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import com.comp5348.store.order.domain.model.Money;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mock implementations of external service ports for local testing.
 * These are used when the actual microservices are not available.
 */
@Configuration
public class MockServicePortsConfiguration {

    @Bean
    public InventoryServicePort mockInventoryServicePort() {
        return new InventoryServicePort() {
            @Override
            public ReserveResult reserve(UUID orderId, String itemId, int quantity) {
                // Mock: Always succeed with a single warehouse allocation
                return ReserveResult.success(List.of(new Allocation("WH-LOCAL", quantity)));
            }

            @Override
            public void release(UUID orderId) {
                // Mock: No-op
            }

            @Override
            public void deduct(UUID orderId) {
                // Mock: No-op
            }
        };
    }

    @Bean
    public PaymentServicePort mockPaymentServicePort() {
        return new PaymentServicePort() {
            @Override
            public PaymentResult authorize(UUID orderId, Money amount, String idempotencyKey) {
                // Mock: Always authorize
                return PaymentResult.authorized();
            }

            @Override
            public PaymentResult refund(UUID orderId) {
                // Mock: Always succeed
                return PaymentResult.authorized();
            }
        };
    }

    @Bean
    public ShippingServicePort mockShippingServicePort() {
        return new ShippingServicePort() {
            @Override
            public ShipmentResult request(UUID orderId, List<InventoryServicePort.Allocation> allocations) {
                // Mock: Always accept with a tracking ID
                return new ShipmentResult(true, "TRACK-" + orderId.toString().substring(0, 8).toUpperCase());
            }
        };
    }

    @Bean
    public NotificationServicePort mockNotificationServicePort() {
        return new NotificationServicePort() {
            @Override
            public void send(UUID orderId, String template, Map<String, String> variables) {
                // Mock: Log and no-op
                System.out.println("Mock Notification: orderId=" + orderId + ", template=" + template);
            }
        };
    }
}

