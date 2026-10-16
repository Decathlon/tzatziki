package com.decathlon.tzatziki.utils;

import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

public class DatabaseCleaner {
    /**
     * This field is a default for tables not to clean. Used to keep them filtered out even after a reset filter call
     */
    private static final List<String> DEFAULT_TABLES_NOT_TO_BE_CLEANED = List.of("flyway_schema_history");
    private static final List<String> TABLES_NOT_TO_BE_CLEANED = new ArrayList<>(DEFAULT_TABLES_NOT_TO_BE_CLEANED);

    /**
     * Add a list of tables that won't be cleaned by autoclean mechanism.
     *
     * @param tables
     */
    public static void addToTablesNotToBeCleaned(List<String> tables) {
        TABLES_NOT_TO_BE_CLEANED.addAll(tables);
    }

    /**
     * Add a tables that won't be cleaned by autoclean mechanism.
     *
     * @param tables
     */
    public static void addToTablesNotToBeCleaned(String... tables) {
        addToTablesNotToBeCleaned(Arrays.asList(tables));
    }

    /**
     * To be used in an @AfterClass if we need to reset the not-to-clean table filter
     */
    public static void resetTablesNotToBeCleanedFilter() {
        TABLES_NOT_TO_BE_CLEANED.clear();
        TABLES_NOT_TO_BE_CLEANED.addAll(DEFAULT_TABLES_NOT_TO_BE_CLEANED);
    }

    public static void clean(DataSource dataSource) {
        clean(dataSource, "public");
    }

    public static void clean(DataSource dataSource, List<String> schemas) {
        schemas.forEach(schema -> clean(dataSource, schema));
    }

    public static void clean(DataSource dataSource, String schema) {
        forAllTables(dataSource, schema, "TRUNCATE %s RESTART IDENTITY CASCADE"::formatted);
    }

    public static void setTriggers(DataSource dataSource, TriggerStatus status) {
        setTriggers(dataSource, "public", status);
    }

    public static void setTriggers(DataSource dataSource, List<String> schemas, TriggerStatus status) {
        schemas.forEach(schema -> setTriggers(dataSource, schema, status));
    }

    public static void setTriggers(DataSource dataSource, String schema, TriggerStatus status) {
        forAllTables(dataSource, schema, table -> "ALTER TABLE %s %s TRIGGER ALL".formatted(table, status));
    }

    @SneakyThrows
    public static void truncateTable(DataSource dataSource, String tableWithSchema) {
        inTransaction(dataSource, connection -> execute(connection, "TRUNCATE %s RESTART IDENTITY CASCADE".formatted(tableWithSchema)));
    }

    @SneakyThrows
    private static void forAllTables(DataSource dataSource, String schema, UnaryOperator<String> sqlForTable) {
        List<String> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String table = resultSet.getString("TABLE_NAME");
                if (TABLES_NOT_TO_BE_CLEANED.stream().noneMatch(table::matches)) {
                    // names read from the metadata are quoted to support mixed-case or special characters
                    tables.add("\"%s\".\"%s\"".formatted(schema, table));
                }
            }
        }
        inTransaction(dataSource, connection -> {
            for (String table : tables) {
                execute(connection, sqlForTable.apply(table));
            }
        });
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql); // NOSONAR identifiers come from the feature files or the database metadata
        }
    }

    /**
     * Runs the action in a transaction committed at the end, whatever the autocommit default of the DataSource.
     */
    static void inTransaction(DataSource dataSource, SqlAction action) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                action.accept(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @FunctionalInterface
    interface SqlAction {
        void accept(Connection connection) throws SQLException;
    }

    @SuppressWarnings("java:S115")
    public enum TriggerStatus {disable, enable}
}
