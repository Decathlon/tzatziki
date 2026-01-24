package com.decathlon.tzatziki.utils;

import lombok.SneakyThrows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

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

    @SneakyThrows
    public static void clean(DataSource dataSource, List<String> schemas) {
        schemas.forEach(schema -> clean(dataSource, schema));
    }

    @SneakyThrows
    public static void clean(DataSource dataSource, String schema) {
        // Since schema name and table name cannot be parameterized in PreparedStatement, we use String#formatted here.
        // This is safe as schema and table names comes from a trusted source.
        executeForAllTables(dataSource, schema, (jdbcTemplate, table)
                -> jdbcTemplate.update("TRUNCATE %s.%s RESTART IDENTITY CASCADE".formatted(schema, table))); // NOSONAR
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
        // Since schema name and table name cannot be parameterized in PreparedStatement, we use String#formatted here.
        // This is safe as schema and table names comes from a trusted source.
        executeForAllTables(dataSource, schema, (jdbcTemplate, table)
                -> jdbcTemplate.update("alter table %s.%s %s trigger all".formatted(schema, table, status))); // NOSONAR
    }

    private static void executeForAllTables(DataSource dataSource, String schema, BiConsumer<JdbcTemplate, String> action) throws SQLException {
        ResultSet resultSet = null;
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        try {
            try (Connection connection = DataSourceUtils.getConnection(dataSource)) {
                resultSet = connection.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"});
            }
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            while (resultSet.next()) {
                String table = resultSet.getString("TABLE_NAME");
                if (TABLES_NOT_TO_BE_CLEANED.stream().noneMatch(table::matches)) {
                    TransactionStatus status = transactionManager.getTransaction(transactionDefinition);
                    action.accept(jdbcTemplate, table);
                    transactionManager.commit(status);
                }
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }


    @SuppressWarnings("java:S115")
    public enum TriggerStatus {disable, enable}


}
