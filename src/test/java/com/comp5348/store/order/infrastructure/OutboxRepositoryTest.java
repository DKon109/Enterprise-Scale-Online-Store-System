package com.comp5348.store.order.infrastructure;

import com.comp5348.store.order.domain.model.OutboxEvent;
import com.comp5348.store.order.infrastructure.outbox.PostgresOutboxEventRepository;
import com.comp5348.store.order.infrastructure.persistence.PostgresConnectionProvider;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

class OutboxRepositoryTest {

    @Test
    void unpublishedEventsReturnedUntilMarked() {
        try (EmbeddedPostgres postgres = startEmbeddedPostgresOrSkip()) {
            PostgresOutboxEventRepository repository = new PostgresOutboxEventRepository(
                    new PostgresConnectionProvider(
                            postgres.getJdbcUrl("postgres", "postgres"),
                            "postgres",
                            "postgres"));
            UUID aggregateId = UUID.randomUUID();

            OutboxEvent event = repository.append(aggregateId, "OrderPlaced", "{}");
            assertFalse(event.isPublished());

            List<OutboxEvent> batch = repository.findUnpublished(10);
            assertEquals(1, batch.size());
            assertEquals(event.getId(), batch.get(0).getId());

            repository.markPublished(event.getId());
            assertTrue(repository.findUnpublished(10).isEmpty());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start embedded Postgres", e);
        }
    }

    private EmbeddedPostgres startEmbeddedPostgresOrSkip() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException | IllegalStateException e) {
            Assumptions.assumeTrue(false, "Embedded Postgres not available: " + e.getMessage());
            throw new IllegalStateException("Embedded Postgres not available", e);
        }
    }
}
