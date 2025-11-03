package com.comp5348.store.order.infrastructure.logging;

import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

public class InterServiceCallLogger {

    private static final Logger LOGGER = Logger.getLogger(InterServiceCallLogger.class.getName());

    public static InterServiceCallLogger noop() {
        return new InterServiceCallLogger(false);
    }

    private final boolean enabled;

    public InterServiceCallLogger() {
        this(true);
    }

    private InterServiceCallLogger(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(UUID orderId, String correlationId, String step, int attempt, long latencyMs, String outcome) {
        if (!enabled) {
            return;
        }
        String json = """
                {"ts":"%s","corrId":"%s","orderId":"%s","step":"%s","attempt":%d,"latencyMs":%d,"outcome":"%s"}
                """.strip()
                .formatted(
                        Instant.now().toString(),
                        correlationId == null ? "n/a" : correlationId,
                        orderId,
                        step,
                        attempt,
                        latencyMs,
                        outcome);
        LOGGER.info(json);
    }
}
