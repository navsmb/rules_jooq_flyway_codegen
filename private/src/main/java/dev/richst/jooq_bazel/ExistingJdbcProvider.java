package dev.richst.jooq_bazel;

import org.apache.commons.lang3.NotImplementedException;

public class ExistingJdbcProvider implements JdbcProvider {

    @Override
    public JdbcProvider start() {
        return this;
    }

    @Override
    public String getDriverClassName() {
        throw new NotImplementedException("ExistingJdbcProvider relies on config files for jooq and flyway and these values are not set");
    }

    @Override
    public String getJdbcUrl() {
        throw new NotImplementedException("ExistingJdbcProvider relies on config files for jooq and flyway and these values are not set");
    }

    @Override
    public String getUsername() {
        throw new NotImplementedException("ExistingJdbcProvider relies on config files for jooq and flyway and these values are not set");
    }

    @Override
    public String getPassword() {
        throw new NotImplementedException("ExistingJdbcProvider relies on config files for jooq and flyway and these values are not set");
    }

    @Override
    public void close() {
    }
}
