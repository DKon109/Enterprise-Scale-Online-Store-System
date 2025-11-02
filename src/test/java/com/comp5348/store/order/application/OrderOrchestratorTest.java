package com.comp5348.store.order.application;

import com.comp5348.store.order.application.event.OutboxPublisher;
import com.comp5348.store.order.application.policy.CircuitBreaker;
import com.comp5348.store.order.application.policy.RetryPolicy;
import com.comp5348.store.order.application.port.InventoryServicePort;
import com.comp5348.store.order.application.port.NotificationServicePort;
import com.comp5348.store.order.application.port.PaymentServicePort;
import com.comp5348.store.order.application.port.ShippingServicePort;
import com.comp5348.store.order.application.service.OrderOrchestrator;
import com.comp5348.store.order.application.service.OrderQueryService;
import com.comp5348.store.order.domain.model.Order;
import com.comp5348.store.order.domain.repository.OrderSagaStateRepository;
import com.comp5348.store.order.infrastructure.logging.InterServiceCallLogger;
import com.comp5348.store.order.infrastructure.outbox.PostgresOutboxEventRepository;
import com.comp5348.store.order.infrastructure.outbox.PersistentOutboxPublisher;
import com.comp5348.store.order.infrastructure.persistence.InMemoryOrderEventRepository;
import com.comp5348.store.order.infrastructure.persistence.InMemoryOrderRepository;
import com.comp5348.store.order.infrastructure.persistence.InMemorySagaStateRepository;
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
        final InMemoryOrderRepository orderRepo;
        final InMemoryOrderEventRepository eventRepo;
        final InMemorySagaStateRepository sagaRepo;
        final PostgresOutboxEventRepository outboxRepo;
        final RecordingInventoryPort inventory;
        final RecordingPaymentPort payments;
        final RecordingShippingPort shipping;
        final RecordingNotificationPort notifications;
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

            this.orderRepo = new InMemoryOrderRepository();
            this.eventRepo = new InMemoryOrderEventRepository();
            this.sagaRepo = new InMemorySagaStateRepository();
            this.outboxRepo = new PostgresOutboxEventRepository(connectionProvider);
            this.inventory = new RecordingInventoryPort();
            this.payments = new RecordingPaymentPort();
            this.shipping = new RecordingShippingPort();
            this.notifications = new RecordingNotificationPort();
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

        @Override
        public ReserveResult reserve(UUID orderId, String itemId, int quantity) {
            reserveCalls++;
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
