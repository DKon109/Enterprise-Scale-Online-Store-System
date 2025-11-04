package com.comp5348.store.order.application.support;

/**
 * Thread-local holder for per-request order processing flags.
 *
 * <p>This gives application-layer components (e.g. HTTP controllers) a way to
 * communicate simulation toggles to downstream adapters without coupling those
 * adapters to the web layer. Currently it only tracks whether delivery failure
 * should be simulated for the active request.</p>
 */
public final class OrderProcessingContext {

    private static final ThreadLocal<Flags> CONTEXT = ThreadLocal.withInitial(Flags::new);

    private OrderProcessingContext() {
        // utility
    }

    /**
     * Enable or disable delivery-failure simulation for the current thread.
     */
    public static void setSimulateDeliveryFailure(boolean simulate) {
        CONTEXT.get().simulateDeliveryFailure = simulate;
    }

    /**
     * @return true when delivery failure should be simulated for the current thread
     */
    public static boolean isDeliveryFailureSimulated() {
        return CONTEXT.get().simulateDeliveryFailure;
    }

    /**
     * Clear any request-scoped flags to avoid leaking state across requests.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    private static final class Flags {
        private boolean simulateDeliveryFailure;
    }
}

