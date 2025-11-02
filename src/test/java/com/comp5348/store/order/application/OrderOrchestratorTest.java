package com.comp5348.store.order.application;

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
import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.domain.repository.OrderSagaStateRepository;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import com.comp5348.store.order.infrastructure.outbox.PostgresOutboxEventRepository;
import com.comp5348.store.order.infrastructure.outbox.PersistentOutboxPublisher;
import com.comp5348.store.order.infrastructure.persistence.JdbcTransactionTemplate;
import com.comp5348.store.order.infrastructure.persistence.PostgresOrderEventRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresOrderRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresSagaStateRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresConnectionProvider;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

class OrderOrchestratorTest {

    @Test
    void happyPathCompletesSagaAndPersistsTimeline() {
        try (TestContext ctx = TestContext.happyPath()) {
            UUID orderId = ctx.orchestrator.placeOrder("customer-1", "SKU-1", 2, "corr-1");

            Order order = ctx.orderRepo.getRequired(orderId);
            assertEquals(Order.Status.SHIPMENT_REQUESTED, order.getStatus());

            var timeline = ctx.eventRepo.findByOrderId(orderId);
            assertEquals(4, timeline.size());
            assertEquals("OrderPlaced", timeline.get(0).getEventType());
            assertEquals("ShipmentRequested", timeline.get(3).getEventType());

            assertTrue(ctx.notifications.sent.isEmpty(), "No notifications expected on happy path");
            assertTrue(ctx.sagaRepo.findById(orderId).isPresent());
            assertEquals("WAITING_FOR_DELIVERY", ctx.sagaRepo.findById(orderId).get().getStep());

            var outboxEvents = ctx.outboxRepo.findUnpublished(10);
            assertFalse(outboxEvents.isEmpty());
        }
    }

    @Test
    void paymentDeclineCancelsOrderAndNotifiesCustomer() {
        try (TestContext ctx = TestContext.happyPath()) {
            ctx.payments.authorizeResult = PaymentServicePort.PaymentResult.declined("DECLINED");

            UUID orderId = ctx.orchestrator.placeOrder("customer-1", "SKU-1", 2, "corr-pay-fail");

            Order order = ctx.orderRepo.getRequired(orderId);
            assertEquals(Order.Status.CANCELLED, order.getStatus());
            assertEquals(1, ctx.inventory.releaseCalls);
            assertEquals("PAYMENT_FAILED", ctx.notifications.sent.get(0).template());
            assertTrue(ctx.sagaRepo.findById(orderId).isEmpty(), "Saga state should be cleared after cancellation");
            assertEquals(0, ctx.shipping.requests);
        }
    }

    @Test
    void stockReservationFailureCancelsOrderAndFinalizesSaga() {
        try (TestContext ctx = TestContext.happyPath()) {
            ctx.inventory.failReservation = true;
            ctx.inventory.failureReason = "OUT_OF_STOCK";

            UUID orderId = ctx.orchestrator.placeOrder("customer-1", "SKU-1", 2, "corr-stock-fail");

            Order order = ctx.orderRepo.getRequired(orderId);
            assertEquals(Order.Status.CANCELLED, order.getStatus());
            assertEquals(0, ctx.inventory.releaseCalls, "release should not be called when reservation fails");
            assertEquals(0, ctx.payments.authorizeCalls, "payment should not be attempted after stock failure");
            assertEquals(4, ctx.inventory.reserveCalls, "should exhaust all retry attempts");

            var timeline = ctx.eventRepo.findByOrderId(orderId);
            assertEquals(2, timeline.size());
            assertEquals("OrderPlaced", timeline.get(0).getEventType());
            assertEquals("StockReservationFailed", timeline.get(1).getEventType());
            assertEquals("OUT_OF_STOCK", timeline.get(1).getPayload().get("reason"));

            var sagaState = ctx.sagaRepo.findById(orderId);
            assertTrue(sagaState.isPresent(), "saga should persist terminal state on failure");
            assertEquals("TERMINAL_FAILED_RESERVATION", sagaState.get().getStep());
            assertTrue(sagaState.get().getLastError().contains("OUT_OF_STOCK"));

            assertEquals(1, ctx.notifications.sent.size());
            assertEquals("OUT_OF_STOCK", ctx.notifications.sent.get(0).template());

            var outboxEvents = ctx.outboxRepo.findUnpublished(10);
            assertEquals(2, outboxEvents.size());
            assertEquals("OrderStatusChanged", outboxEvents.get(1).getType());
            assertTrue(outboxEvents.get(1).getPayload().contains("\"status\":\"CANCELLED\""));
        }
    }

    @Test
    void cancelBeforeShipmentTriggersRefundAndRelease() {
        try (TestContext ctx = TestContext.happyPath()) {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(orderId, "customer-1", "SKU-1", 2);
            order.markReserved();
            order.markPaid();
            ctx.orderRepo.save(order);

            ctx.orchestrator.cancel(orderId, "corr-cancel");

            Order reloaded = ctx.orderRepo.getRequired(orderId);
            assertEquals(Order.Status.CANCELLED, reloaded.getStatus());
            assertEquals(1, ctx.inventory.releaseCalls);
            assertEquals(1, ctx.payments.refundCalls);
        }
    }

    @Test
    void paymentRetryUsesSameIdempotencyKey() {
        try (TestContext ctx = TestContext.happyPath()) {
            ctx.payments.failFirstAttempt = true;

            UUID orderId = ctx.orchestrator.placeOrder("customer-1", "SKU-1", 2, "corr-retry");

            assertEquals(2, ctx.payments.authorizeCalls);
            assertEquals(1, new java.util.HashSet<>(ctx.payments.idempotencyKeys).size());
            assertTrue(ctx.payments.idempotencyKeys.contains("order-" + orderId));
        }
    }

    private static class TestContext implements AutoCloseable {
        final EmbeddedPostgres postgres;
        final PostgresOrderRepository orderRepo;
        final PostgresOrderEventRepository eventRepo;
        final PostgresSagaStateRepository sagaRepo;
        final PostgresOutboxEventRepository outboxRepo;
        final RecordingInventoryPort inventory;
        final RecordingPaymentPort payments;
        final RecordingShippingPort shipping;
        final RecordingNotificationPort notifications;
        final TransactionTemplate transactions;
        final RetryPolicy retryPolicy;
        final CircuitBreaker circuitBreaker;
        final OutboxPublisher outboxPublisher;
        final OrderOrchestrator orchestrator;
        final OrderQueryService queries;

        private TestContext() {
            this.postgres = startEmbeddedPostgresOrSkip();

            PostgresConnectionProvider connectionProvider = new PostgresConnectionProvider(
                    postgres.getJdbcUrl("postgres", "postgres"),
                    "postgres",
                    "postgres");

            this.orderRepo = new PostgresOrderRepository(connectionProvider);
            this.eventRepo = new PostgresOrderEventRepository(connectionProvider);
            this.sagaRepo = new PostgresSagaStateRepository(connectionProvider);
            this.outboxRepo = new PostgresOutboxEventRepository(connectionProvider);
            this.inventory = new RecordingInventoryPort();
            this.payments = new RecordingPaymentPort();
            this.shipping = new RecordingShippingPort();
            this.notifications = new RecordingNotificationPort();
            this.transactions = new JdbcTransactionTemplate(connectionProvider);
            this.retryPolicy = new RetryPolicy(
                    new Duration[]{Duration.ZERO, Duration.ZERO, Duration.ZERO},
                    RetryPolicy.Sleeper.NOOP);
            this.circuitBreaker = new CircuitBreaker(3, Duration.ofSeconds(5));
            this.outboxPublisher = new PersistentOutboxPublisher(outboxRepo);
            this.orchestrator = new OrderOrchestrator(
                    orderRepo,
                    inventory,
                    payments,
                    shipping,
                    notifications,
                    transactions,
                    retryPolicy,
                    circuitBreaker,
                    outboxPublisher,
                    sagaRepo,
                    eventRepo,
                    InterServiceCallLogger.noop());
            this.queries = new OrderQueryService(orderRepo, eventRepo);
        }

        static TestContext happyPath() {
            return new TestContext();
        }

        @Override
        public void close() {
            try {
                postgres.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to stop embedded Postgres", e);
            }
        }

        private static EmbeddedPostgres startEmbeddedPostgresOrSkip() {
            try {
                return EmbeddedPostgres.start();
            } catch (IOException | IllegalStateException e) {
                Assumptions.assumeTrue(false, "Embedded Postgres not available: " + e.getMessage());
                throw new IllegalStateException("Embedded Postgres not available", e);
            }
        }
    }

    private static class RecordingInventoryPort implements InventoryServicePort {
        int reserveCalls = 0;
        int releaseCalls = 0;
        boolean failReservation = false;
        String failureReason = "UNAVAILABLE";
        RuntimeException exceptionOnReserve;

        @Override
        public ReserveResult reserve(UUID orderId, String itemId, int quantity) {
            reserveCalls++;
            if (exceptionOnReserve != null) {
                throw exceptionOnReserve;
            }
            if (failReservation) {
                return ReserveResult.failure(failureReason);
            }
            return ReserveResult.success(List.of(new Allocation("WH-1", quantity)));
        }

        @Override
        public void release(UUID orderId) {
            releaseCalls++;
        }

        @Override
        public void deduct(UUID orderId) {
        }
    }

    private static class RecordingPaymentPort implements PaymentServicePort {
        int authorizeCalls = 0;
        int refundCalls = 0;
        boolean failFirstAttempt = false;
        PaymentResult authorizeResult = PaymentResult.authorized();
        final List<String> idempotencyKeys = new ArrayList<>();

        @Override
        public PaymentResult authorize(UUID orderId, com.comp5348.store.order.domain.model.Money amount, String idempotencyKey) {
            authorizeCalls++;
            idempotencyKeys.add(idempotencyKey);
            if (failFirstAttempt && authorizeCalls == 1) {
                throw new RuntimeException("Transient failure");
            }
            return authorizeResult;
        }

        @Override
        public PaymentResult refund(UUID orderId) {
            refundCalls++;
            return PaymentResult.authorized();
        }
    }

    private static class RecordingShippingPort implements ShippingServicePort {
        int requests = 0;
        ShipmentResult nextResult = new ShipmentResult(true, "TRACK-123");

        @Override
        public ShipmentResult request(UUID orderId, List<InventoryServicePort.Allocation> allocations) {
            requests++;
            return nextResult;
        }
    }

    private static class RecordingNotificationPort implements NotificationServicePort {
        record Notification(UUID orderId, String template, java.util.Map<String, String> vars) { }
        final List<Notification> sent = new ArrayList<>();

        @Override
        public void send(UUID orderId, String template, java.util.Map<String, String> variables) {
            sent.add(new Notification(orderId, template, variables));
        }
    }
}
