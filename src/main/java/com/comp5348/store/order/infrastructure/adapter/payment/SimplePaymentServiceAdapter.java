package com.comp5348.store.order.infrastructure.adapter.payment;

import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.model.Money;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simple, in-memory payment adapter. Always authorises charges and records the order so
 * subsequent refunds can be marked successful. Useful for local testing without integrating
 * a real payment gateway.
 */
@Component
public class SimplePaymentServiceAdapter implements PaymentServicePort {

    private static final Logger log = LoggerFactory.getLogger(SimplePaymentServiceAdapter.class);

    private final Set<UUID> authorisedOrders = ConcurrentHashMap.newKeySet();

    @Override
    public PaymentResult authorize(UUID orderId, Money amount, String idempotencyKey) {
        authorisedOrders.add(orderId);
        log.debug("Authorised payment for order {} amount {} with key {}", orderId, amount, idempotencyKey);
        return PaymentResult.authorized();
    }

    @Override
    public PaymentResult refund(UUID orderId) {
        boolean previouslyAuthorised = authorisedOrders.remove(orderId);
        if (previouslyAuthorised) {
            log.debug("Refunded payment for order {}", orderId);
            return PaymentResult.authorized();
        }
        log.warn("Refund requested for non-authorised order {}", orderId);
        return PaymentResult.declined("ORDER_NOT_AUTHORISED");
    }
}
