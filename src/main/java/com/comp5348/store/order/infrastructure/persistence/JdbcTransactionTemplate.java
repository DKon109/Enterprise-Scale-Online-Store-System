package com.comp5348.store.order.infrastructure.persistence;

import com.comp5348.store.order.application.support.TransactionTemplate;
import java.util.Objects;
import java.util.function.Supplier;

public class JdbcTransactionTemplate implements TransactionTemplate {

    private final PostgresConnectionProvider connectionProvider;

    public JdbcTransactionTemplate(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        return connectionProvider.withTransaction(action);
    }
}

