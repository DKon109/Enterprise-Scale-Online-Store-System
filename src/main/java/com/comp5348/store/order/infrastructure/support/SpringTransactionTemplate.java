package com.comp5348.store.order.infrastructure.support;

import com.comp5348.store.order.application.support.TransactionTemplate;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Adapter around Spring's {@link org.springframework.transaction.support.TransactionTemplate}
 * to satisfy the framework-agnostic {@link TransactionTemplate} abstraction used by the
 * orchestrator. This keeps the application layer decoupled from Spring specifics while still
 * leveraging declarative transaction management.
 */
public class SpringTransactionTemplate implements TransactionTemplate {

    private final org.springframework.transaction.support.TransactionTemplate delegate;

    public SpringTransactionTemplate(PlatformTransactionManager transactionManager) {
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.delegate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        return delegate.execute(status -> action.get());
    }
}
