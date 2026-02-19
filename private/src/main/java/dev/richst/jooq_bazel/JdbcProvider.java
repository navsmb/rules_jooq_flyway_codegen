package dev.richst.jooq_bazel;

import java.io.Closeable;

public interface JdbcProvider extends Closeable {
    JdbcProvider start();
    String getDriverClassName();
    String getJdbcUrl();
    String getUsername();
    String getPassword();
}
