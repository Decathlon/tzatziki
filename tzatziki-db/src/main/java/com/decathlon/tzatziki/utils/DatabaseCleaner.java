package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.utils.sql.SqlIdentifier;
import com.decathlon.tzatziki.utils.sql.SqlRenderer;
import com.decathlon.tzatziki.utils.sql.TruncateSpec;
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

public class DatabaseCleaner {

    /**
     * Validates that the given name is a safe SQL identifier.
     *
     * @throws IllegalArgumentException if the name contains invalid characters
     */
    static void validateIdentifier(String name, String context) {
        SqlIdentifier.validate(name, context);
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
        SqlIdentifier.schema(schema);
        executeForAllTables(dataSource, schema, (connection, table)
                -> executeUpdate(connection, SqlRenderer.render(TruncateSpec.table(schema + "." + table).build())));
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
        TruncateSpec truncateSpec = TruncateSpec.table(tableWithSchema).build();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(SqlRenderer.render(truncateSpec)); // NOSONAR
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
