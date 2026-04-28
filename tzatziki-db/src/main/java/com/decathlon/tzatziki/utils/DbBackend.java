package com.decathlon.tzatziki.utils;

import java.util.List;
import java.util.Map;

/**
 * Backend interface for pure table-level database operations.
 * <p>
 * The default implementation ({@link JdbcBackend}) uses raw JDBC.
 * When tzatziki-jpa is on the classpath, a JPA-aware backend can be registered
 * to override the default behavior (entity graph optimization, type conversion, etc.).
 */
public interface DbBackend {

    /**
     * Insert rows into a table.
     *
     * @param table the table name
     * @param rows  list of column-name → value maps representing rows to insert
     */
    void insertRows(String table, List<Map<String, Object>> rows);

    /**
     * Query all rows from a table.
     * The expectedRows parameter provides the expected structure (nested maps for relationships)
     * which backends can use for optimization (e.g., JPA entity graph building).
     * If expectedRows is null or empty, returns all columns.
     *
     * @param table        the table name
     * @param expectedRows expected rows with their structure (used for query optimization)
     * @return list of column-name → value maps
     */
    List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows);

    /**
     * Count all rows in a table.
     */
    long count(String table);

    /**
     * Truncate/delete all rows from a table.
     */
    void truncate(String table);
}
