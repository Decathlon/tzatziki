package com.decathlon.tzatziki.utils;

import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import static java.util.stream.Collectors.joining;

/**
 * Default DbBackend implementation using pure JDBC (PostgreSQL).
 */
public class JdbcBackend implements DbBackend {

    private final DataSource dataSource;

    public JdbcBackend(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @SneakyThrows
    public void insertRows(String table, List<Map<String, Object>> rows) {
        DatabaseCleaner.inTransaction(dataSource, connection -> {
            for (Map<String, Object> row : rows) {
                List<String> columns = new ArrayList<>(row.keySet());
                String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(
                        table, String.join(", ", columns), columns.stream().map(column -> "?").collect(joining(", ")));
                try (PreparedStatement statement = connection.prepareStatement(sql)) { // NOSONAR identifiers come from the feature files
                    for (int i = 0; i < columns.size(); i++) {
                        // untyped parameters: PostgreSQL casts them to the column types
                        statement.setObject(i + 1, row.get(columns.get(i)), java.sql.Types.OTHER);
                    }
                    statement.executeUpdate();
                }
            }
        });
    }

    @Override
    @SneakyThrows
    public List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + table)) { // NOSONAR
            ResultSetMetaData meta = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), normalizeValue(resultSet.getObject(i)));
                }
                results.add(row);
            }
        }
        return results;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof java.sql.Time time) return time.toLocalTime();
        return value;
    }

    @Override
    @SneakyThrows
    public long count(String table) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { // NOSONAR
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    @Override
    public void truncate(String table) {
        DatabaseCleaner.truncateTable(dataSource, table);
    }
}
