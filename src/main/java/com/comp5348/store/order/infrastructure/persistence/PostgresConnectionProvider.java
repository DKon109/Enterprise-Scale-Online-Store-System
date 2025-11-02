package com.comp5348.store.order.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Provides JDBC connections to a PostgreSQL database.
 */
public class PostgresConnectionProvider {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresConnectionProvider(String jdbcUrl, String username, String password) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        loadDriver();
    }

    private void loadDriver() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL JDBC driver not found on the classpath", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (username.isEmpty() && password.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}

