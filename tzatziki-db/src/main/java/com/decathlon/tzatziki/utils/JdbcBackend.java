package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.utils.sql.*;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Default DbBackend implementation using pure JDBC with the internal SQL DSL
 * to model identifiers and statement rendering.
 * Performs INSERT, SELECT, COUNT, and TRUNCATE operations without any ORM.
 */
public class JdbcBackend implements DbBackend {

    private final DataSource dataSource;

    public JdbcBackend(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @SneakyThrows
    public void insertRows(String table, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;

        // Collect all column names from all rows (some rows may have different keys)
        Set<String> allColumns = new LinkedHashSet<>();
        rows.forEach(row -> allColumns.addAll(row.keySet()));
        InsertSpec insertSpec = InsertSpec.into(table).columns(allColumns);
        String sql = SqlRenderer.render(insertSpec);

        // Resolve column types from DB metadata
        Map<String, Integer> columnTypes = resolveColumnTypes(insertSpec.table().value(), insertSpec.columns());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                int idx = 1;
                for (SqlIdentifier col : insertSpec.columns()) {
                    String columnName = col.value();
                    Object value = row.get(columnName);
                    if (value == null) {
                        int sqlType = columnTypes.getOrDefault(columnName, java.sql.Types.VARCHAR);
                        ps.setNull(idx, sqlType);
                    } else if (value instanceof String strVal) {
                        int sqlType = columnTypes.getOrDefault(columnName, java.sql.Types.VARCHAR);
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
    private Map<String, Integer> resolveColumnTypes(String table, List<SqlIdentifier> columns) {
        Map<String, Integer> types = new HashMap<>();
        Set<String> columnNames = columns.stream().map(SqlIdentifier::value).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                // Case-insensitive column matching (PostgreSQL returns lowercase)
                String matchedCol = columnNames.stream()
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
                    String matchedCol = columnNames.stream()
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
        Set<String> columns = new LinkedHashSet<>();
        if (expectedRows != null) {
            expectedRows.forEach(row -> columns.addAll(row.keySet()));
        }
        SelectSpec selectSpec = columns.isEmpty()
                ? SelectSpec.from(table).allColumns()
                : SelectSpec.from(table).columns(columns);
        String sql = SqlRenderer.render(selectSpec);

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
        String sql = SqlRenderer.render(CountSpec.from(table));
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
