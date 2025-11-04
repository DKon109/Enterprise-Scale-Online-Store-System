package com.comp5348.store.order.infrastructure.adapter.payment;

import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.model.Money;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * HTTP-based payment adapter that calls the Bank service API.
 *
 * Integrates with Bank service at:
 * - POST /transactions (create payment transaction)
 *
 * Supports X-Simulate-Payment-Failure header for testing failure scenarios.
 */
@Component
public class SimplePaymentServiceAdapter implements PaymentServicePort {

    private static final Logger log = LoggerFactory.getLogger(SimplePaymentServiceAdapter.class);

    private final RestTemplate restTemplate;
    private final String bankServiceUrl;

    public SimplePaymentServiceAdapter(RestTemplate restTemplate, @Value("${bank.service.url:http://localhost:8081}") String bankServiceUrl) {
        this.restTemplate = restTemplate;
        this.bankServiceUrl = bankServiceUrl;
    }

    @Override
    public PaymentResult authorize(UUID orderId, Money amount, String idempotencyKey) {
        // Check for payment failure simulation header
        if (isPaymentFailureSimulated()) {
            log.debug("Payment failure simulated for order {} (X-Simulate-Payment-Failure header present)", orderId);
            return PaymentResult.declined("SIMULATED_PAYMENT_FAILURE");
        }

        try {
            // Call Bank service to create a purchase transaction
            String url = bankServiceUrl + "/transactions";
            double amountValue = amount.amount().doubleValue();
            PaymentTransactionRequest request = new PaymentTransactionRequest(
                    orderId,
                    amountValue,
                    "Purchase",
                    idempotencyKey
            );

            PaymentTransactionResponse response = restTemplate.postForObject(url, request, PaymentTransactionResponse.class);

            if (response != null && "Confirmed".equals(response.status)) {
                log.debug("Authorised payment for order {} amount {} with key {}", orderId, amount, idempotencyKey);
                return PaymentResult.authorized();
            } else {
                log.warn("Payment authorization failed for order {}: {}", orderId, response);
                return PaymentResult.declined("BANK_DECLINED");
            }
        } catch (RestClientException e) {
            log.error("Bank service error during payment authorization for order {}: {}", orderId, e.getMessage());
            return PaymentResult.declined("BANK_SERVICE_ERROR");
        }
    }

    @Override
    public PaymentResult refund(UUID orderId) {
        try {
            // Call Bank service to create a refund transaction
            String url = bankServiceUrl + "/transactions";
            PaymentTransactionRequest request = new PaymentTransactionRequest(
                    orderId,
                    null,
                    "Refund",
                    "refund-" + orderId
            );

            PaymentTransactionResponse response = restTemplate.postForObject(url, request, PaymentTransactionResponse.class);

            if (response != null && "Confirmed".equals(response.status)) {
                log.debug("Refunded payment for order {}", orderId);
                return PaymentResult.authorized();
            } else {
                log.warn("Refund failed for order {}: {}", orderId, response);
                return PaymentResult.declined("REFUND_FAILED");
            }
        } catch (RestClientException e) {
            log.error("Bank service error during refund for order {}: {}", orderId, e.getMessage());
            return PaymentResult.declined("BANK_SERVICE_ERROR");
        }
    }

    /**
     * Check if the X-Simulate-Payment-Failure header is present in the current request.
     * This allows testing payment failure scenarios without a real payment gateway.
     */
    private boolean isPaymentFailureSimulated() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String header = request.getHeader("X-Simulate-Payment-Failure");
                return "true".equalsIgnoreCase(header);
            }
        } catch (Exception e) {
            log.debug("Could not check for payment failure simulation header", e);
        }
        return false;
    }

    /**
     * DTO for Bank service payment transaction request
     */
    public static class PaymentTransactionRequest {
        public UUID orderId;
        public Double amount;
        public String type;
        public String idempotencyKey;

        public PaymentTransactionRequest(UUID orderId, Double amount, String type, String idempotencyKey) {
            this.orderId = orderId;
            this.amount = amount;
            this.type = type;
            this.idempotencyKey = idempotencyKey;
        }
    }

    /**
     * DTO for Bank service payment transaction response
     */
    public static class PaymentTransactionResponse {
        public Long id;
        public Double amount;
        public String type;
        public String status;
        public String bankReferenceID;
        public UUID orderId;
        public String idempotencyKey;
        public String correlationId;
        public LocalDateTime timestamp;
    }
}
