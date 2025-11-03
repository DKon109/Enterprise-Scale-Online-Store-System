package com.comp5348.store.order.application.port;

import com.comp5348.store.order.model.Money;
import java.util.UUID;

public interface PaymentServicePort {

    PaymentResult authorize(UUID orderId, Money amount, String idempotencyKey);

    PaymentResult refund(UUID orderId);

    final class PaymentResult {
        private final boolean authorized;
        private final String reason;

        private PaymentResult(boolean authorized, String reason) {
            this.authorized = authorized;
            this.reason = reason;
        }

        public static PaymentResult authorized() {
            return new PaymentResult(true, null);
        }

        public static PaymentResult declined(String reason) {
            return new PaymentResult(false, reason);
        }

        public boolean isAuthorized() {
            return authorized;
        }

        public String reason() {
            return reason;
        }
    }
}
