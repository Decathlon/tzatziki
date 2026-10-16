package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.utils.sql.Sql;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Default DbBackend implementation using pure JDBC.
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
        String sql = Sql.insert(table, allColumns);

        // Resolve column types from DB metadata
        Map<String, Integer> columnTypes = resolveColumnTypes(table, allColumns);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                int idx = 1;
                for (String columnName : allColumns) {
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
    private Map<String, Integer> resolveColumnTypes(String table, Collection<String> columns) {
        int dot = table.lastIndexOf('.');
        String schema = dot < 0 ? null : table.substring(0, dot);
        String tableName = table.substring(dot + 1);
        try (Connection connection = dataSource.getConnection()) {
            String schemaPattern = schema != null ? schema : connection.getSchema();
            Map<String, Integer> types = readColumnTypes(connection, schemaPattern, tableName, columns);
            // Unquoted identifiers are stored in lower case by PostgreSQL
            if (types.isEmpty() && !table.equals(table.toLowerCase())) {
                types = readColumnTypes(connection, schemaPattern == null ? null : schemaPattern.toLowerCase(), tableName.toLowerCase(), columns);
            }
            return types;
        }
    }

    private Map<String, Integer> readColumnTypes(Connection connection, String schemaPattern, String tableName, Collection<String> columns) throws java.sql.SQLException {
        Map<String, Integer> types = new HashMap<>();
        try (ResultSet rs = connection.getMetaData().getColumns(null, schemaPattern, tableName, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                for (String column : columns) {
                    if (column.equalsIgnoreCase(colName)) {
                        types.put(column, rs.getInt("DATA_TYPE"));
                        break;
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
            case java.sql.Types.DATE -> java.sql.Date.valueOf(LocalDate.parse(value));
            case java.sql.Types.TIME, java.sql.Types.TIME_WITH_TIMEZONE -> java.sql.Time.valueOf(LocalTime.parse(value));
            case java.sql.Types.TIMESTAMP, java.sql.Types.TIMESTAMP_WITH_TIMEZONE -> parseTimestamp(value);
            default -> value;
        };
    }

    private static Timestamp parseTimestamp(String value) {
        try {
            return Timestamp.from(Instant.parse(value));
        } catch (Exception ignored) {
            try {
                return Timestamp.from(OffsetDateTime.parse(value).toInstant());
            } catch (Exception ignoredAgain) {
                return value.contains("T") ? Timestamp.valueOf(LocalDateTime.parse(value)) : Timestamp.valueOf(value);
            }
        }
    }

    @Override
    @SneakyThrows
    public List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows) {
        String sql = Sql.select(table);

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
                    Object value = normalizeValue(rs.getObject(i));
                    row.put(colName, value);
                }
                results.add(row);
            }
        }
        return results;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime();
        }
        return value;
    }

    @Override
    @SneakyThrows
    public long count(String table) {
        String sql = Sql.count(table);
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
