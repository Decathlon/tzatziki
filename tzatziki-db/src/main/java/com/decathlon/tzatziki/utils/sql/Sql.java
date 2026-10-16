package com.decathlon.tzatziki.utils.sql;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Renders the few SQL statements used by tzatziki-db. Table and column names are validated through {@link SqlIdentifier}.
 */
public final class Sql {

    private Sql() {
    }

    public static String insert(String table, Collection<String> columns) {
        return "INSERT INTO %s (%s) VALUES (%s)".formatted(
                SqlIdentifier.table(table).value(),
                columnList(columns),
                columns.stream().map(column -> "?").collect(Collectors.joining(", ")));
    }

    public static String select(String table) {
        return "SELECT * FROM %s".formatted(SqlIdentifier.table(table).value());
    }

    public static String count(String table) {
        return "SELECT COUNT(*) FROM %s".formatted(SqlIdentifier.table(table).value());
    }

    public static String truncate(String table) {
        return truncate(SqlIdentifier.table(table));
    }

    public static String truncate(SqlIdentifier table) {
        return "TRUNCATE %s RESTART IDENTITY CASCADE".formatted(table.value());
    }

    private static String columnList(Collection<String> columns) {
        return SqlIdentifier.columns(columns).stream().map(SqlIdentifier::value).collect(Collectors.joining(", "));
    }
}
