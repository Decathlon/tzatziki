package com.decathlon.tzatziki.utils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default DbBackend implementation using pure JDBC.
 * Performs INSERT, SELECT, COUNT, and TRUNCATE operations without any ORM.
 */
@Slf4j
public class JdbcBackend implements DbBackend {

    private final DataSource dataSource;

    public JdbcBackend(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @SneakyThrows
    public void insertRows(String table, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        DatabaseCleaner.validateIdentifier(table, "table name");

        // Collect all column names from all rows (some rows may have different keys)
        Set<String> allColumns = new LinkedHashSet<>();
        rows.forEach(row -> allColumns.addAll(row.keySet()));
        allColumns.forEach(col -> DatabaseCleaner.validateIdentifier(col, "column name"));

        String columns = allColumns.stream().collect(Collectors.joining(", "));
        String placeholders = allColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(table, columns, placeholders);

        // Resolve column types from DB metadata
        Map<String, Integer> columnTypes = resolveColumnTypes(table, allColumns);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                int idx = 1;
                for (String col : allColumns) {
                    Object value = row.get(col);
                    if (value == null) {
                        int sqlType = columnTypes.getOrDefault(col, java.sql.Types.VARCHAR);
                        ps.setNull(idx, sqlType);
                    } else if (value instanceof String strVal) {
                        int sqlType = columnTypes.getOrDefault(col, java.sql.Types.VARCHAR);
                        ps.setObject(idx, convertStringToType(strVal, sqlType), sqlType);
                    } else {
                        ps.setObject(idx, value);
                    }
                    idx++;
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @SneakyThrows
    private Map<String, Integer> resolveColumnTypes(String table, Set<String> columns) {
        Map<String, Integer> types = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                // Case-insensitive column matching (PostgreSQL returns lowercase)
                String matchedCol = columns.stream()
                        .filter(c -> c.equalsIgnoreCase(colName))
                        .findFirst().orElse(null);
                if (matchedCol != null) {
                    types.put(matchedCol, rs.getInt("DATA_TYPE"));
                }
            }
        }
        // Try lowercase table name if nothing found (PostgreSQL stores names in lower case)
        if (types.isEmpty() && !table.equals(table.toLowerCase())) {
            try (Connection connection = dataSource.getConnection();
                 ResultSet rs = connection.getMetaData().getColumns(null, null, table.toLowerCase(), null)) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String matchedCol = columns.stream()
                            .filter(c -> c.equalsIgnoreCase(colName))
                            .findFirst().orElse(null);
                    if (matchedCol != null) {
                        types.put(matchedCol, rs.getInt("DATA_TYPE"));
                    }
                }
            }
        }
        return types;
    }

    private Object convertStringToType(String value, int sqlType) {
        return switch (sqlType) {
            case java.sql.Types.INTEGER, java.sql.Types.SMALLINT, java.sql.Types.TINYINT ->
                    Integer.parseInt(value);
            case java.sql.Types.BIGINT -> Long.parseLong(value);
            case java.sql.Types.FLOAT, java.sql.Types.REAL -> Float.parseFloat(value);
            case java.sql.Types.DOUBLE -> Double.parseDouble(value);
            case java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> new java.math.BigDecimal(value);
            case java.sql.Types.BOOLEAN, java.sql.Types.BIT -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    @Override
    @SneakyThrows
    public List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows) {
        DatabaseCleaner.validateIdentifier(table, "table name");
        Set<String> columns = new LinkedHashSet<>();
        if (expectedRows != null) {
            expectedRows.forEach(row -> columns.addAll(row.keySet()));
        }
        columns.forEach(col -> DatabaseCleaner.validateIdentifier(col, "column name"));
        String columnSelection = columns.isEmpty() ? "*" :
                columns.stream().collect(Collectors.joining(", "));
        String sql = "SELECT %s FROM %s".formatted(columnSelection, table);

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String colName = meta.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(colName, value);
                }
                results.add(row);
            }
        }
        return results;
    }

    @Override
    @SneakyThrows
    public long count(String table) {
        DatabaseCleaner.validateIdentifier(table, "table name");
        String sql = "SELECT COUNT(*) FROM %s".formatted(table);
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Override
    @SneakyThrows
    public void truncate(String table) {
        DatabaseCleaner.truncateTable(dataSource, table);
    }
}
