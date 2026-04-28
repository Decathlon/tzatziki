package com.decathlon.tzatziki.utils;

import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public class DatabaseCleaner {

    /**
     * Regex pattern for valid SQL identifiers (table/schema names).
     * Prevents SQL injection by rejecting anything that is not a valid identifier.
     */
    private static final Pattern VALID_SQL_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");

    /**
     * Validates that the given name is a safe SQL identifier.
     *
     * @throws IllegalArgumentException if the name contains invalid characters
     */
    static void validateIdentifier(String name, String context) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(context + " must not be null or empty");
        }
        if (!VALID_SQL_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid " + context + ": '" + name + "'. Only letters, digits, underscores, and dots (for schema.table) are allowed.");
        }
    }

    /**
     * This field is a default for tables not to clean. Used to keep them filtered out even after a reset filter call
     */
    private static final List<String> DEFAULT_TABLES_NOT_TO_BE_CLEANED = List.of("flyway_schema_history");
    private static final List<String> TABLES_NOT_TO_BE_CLEANED = new CopyOnWriteArrayList<>(DEFAULT_TABLES_NOT_TO_BE_CLEANED);

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
        synchronized (TABLES_NOT_TO_BE_CLEANED) {
            TABLES_NOT_TO_BE_CLEANED.clear();
            TABLES_NOT_TO_BE_CLEANED.addAll(DEFAULT_TABLES_NOT_TO_BE_CLEANED);
        }
    }

    public static void clean(DataSource dataSource) {
        clean(dataSource, "public");
    }

    @SneakyThrows
    public static void clean(DataSource dataSource, List<String> schemas) {
        schemas.forEach(schema -> clean(dataSource, schema));
    }

    @SneakyThrows
    public static void clean(DataSource dataSource, String schema) {
        validateIdentifier(schema, "schema name");
        executeForAllTables(dataSource, schema, (connection, table)
                -> executeUpdate(connection, "TRUNCATE %s.%s RESTART IDENTITY CASCADE".formatted(schema, table)));
    }

    @SneakyThrows
    public static void setTriggers(DataSource dataSource, TriggerStatus status) {
        setTriggers(dataSource, "public", status);
    }

    @SneakyThrows
    public static void setTriggers(DataSource dataSource, List<String> schemas, TriggerStatus status) {
        schemas.forEach(schema -> setTriggers(dataSource, schema, status));
    }

    @SneakyThrows
    public static void setTriggers(DataSource dataSource, String schema, TriggerStatus status) {
        validateIdentifier(schema, "schema name");
        executeForAllTables(dataSource, schema, (connection, table)
                -> executeUpdate(connection, "alter table %s.%s %s trigger all".formatted(schema, table, status)));
    }

    @SneakyThrows
    public static void truncateTable(DataSource dataSource, String tableWithSchema) {
        validateIdentifier(tableWithSchema, "table name");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE %s RESTART IDENTITY CASCADE".formatted(tableWithSchema)); // NOSONAR
        }
    }

    @SneakyThrows
    private static void executeUpdate(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql); // NOSONAR
        }
    }

    private static void executeForAllTables(DataSource dataSource, String schema, BiConsumer<Connection, String> action) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (ResultSet resultSet = connection.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    String table = resultSet.getString("TABLE_NAME");
                    if (TABLES_NOT_TO_BE_CLEANED.stream().noneMatch(excluded -> excluded.equals(table) || table.matches(excluded))) {
                        action.accept(connection, table);
                        connection.commit();
                    }
                }
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }


    @SuppressWarnings("java:S115")
    public enum TriggerStatus {disable, enable}


}
