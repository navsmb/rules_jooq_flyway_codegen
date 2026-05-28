package dev.richst.jooq_bazel;

import org.testcontainers.containers.JdbcDatabaseContainer;

import java.io.Closeable;

public class TestContainersJdbcProvider implements JdbcProvider, Closeable {

    private final JdbcDatabaseContainer jdbcContainer;

    TestContainersJdbcProvider(JdbcDatabaseContainer jdbcContainer) {
        this.jdbcContainer = jdbcContainer;
    }

    @Override
    public JdbcProvider start() {
        this.jdbcContainer.start();
        return this;
    }

    @Override
    public String getDriverClassName() {
        return this.jdbcContainer.getDriverClassName();
    }

    @Override
    public String getJdbcUrl() {
        return this.jdbcContainer.getJdbcUrl();
    }

    @Override
    public String getUsername() {
        return this.jdbcContainer.getUsername();
    }

    @Override
    public String getPassword() {
        return this.jdbcContainer.getPassword();
    }

    @Override
    public void close() {
        jdbcContainer.close();
    }
}
