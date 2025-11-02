package com.comp5348.store.order.infrastructure.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Provides JDBC connections to a PostgreSQL database.
 */
public class PostgresConnectionProvider {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final ThreadLocal<Connection> transactionalConnection = new ThreadLocal<>();

    public PostgresConnectionProvider(String jdbcUrl, String username, String password) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        loadDriver();
        DatabaseMigrations.run(this);
    }

    private void loadDriver() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL JDBC driver not found on the classpath", e);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection current = transactionalConnection.get();
        if (current != null) {
            return wrapTransactionalConnection(current);
        }
        return openConnection();
    }

    public <T> T withTransaction(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        Connection existing = transactionalConnection.get();
        if (existing != null) {
            return action.get();
        }

        Connection connection = null;
        try {
            connection = openConnection();
            connection.setAutoCommit(false);
            transactionalConnection.set(connection);
            T result = action.get();
            connection.commit();
            return result;
        } catch (RuntimeException | Error ex) {
            rollbackQuietly(connection);
            throw ex;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new IllegalStateException("Transaction failed", ex);
        } finally {
            transactionalConnection.remove();
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
                closeQuietly(connection);
            }
        }
    }

    private Connection openConnection() throws SQLException {
        if (username.isEmpty() && password.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private Connection wrapTransactionalConnection(Connection connection) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName())) {
                    return null;
                }
                try {
                    return method.invoke(connection, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler);
    }

    private void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
