package com.comp5348.store.order.application.support;

import java.util.Objects;
import java.util.function.Supplier;

public interface TransactionTemplate {

    <T> T execute(Supplier<T> action);

    default void execute(Runnable action) {
        Objects.requireNonNull(action, "action");
        execute(() -> {
            action.run();
            return null;
        });
    }
}

