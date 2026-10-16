package com.decathlon.tzatziki.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

final class JdbcTransactions {

    private JdbcTransactions() {
    }

    static void execute(DataSource dataSource, Action action) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                action.accept(connection);
                connection.commit();
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(autoCommit);
                } catch (SQLException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            connection.setAutoCommit(autoCommit);
        }
    }

    @FunctionalInterface
    interface Action {
        void accept(Connection connection) throws SQLException;
    }
}
