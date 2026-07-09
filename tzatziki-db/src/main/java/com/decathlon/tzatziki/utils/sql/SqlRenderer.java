package com.decathlon.tzatziki.utils.sql;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class SqlRenderer {

    private SqlRenderer() {
    }

    public static String render(InsertSpec spec) {
        return "INSERT INTO %s (%s) VALUES (%s)".formatted(
                render(spec.table()),
                render(spec.columns()),
                placeholders(spec.columns().size())
        );
    }

    public static String render(SelectSpec spec) {
        String columnSelection = spec.columns().isEmpty() ? "*" : render(spec.columns());
        return "SELECT %s FROM %s".formatted(columnSelection, render(spec.table()));
    }

    public static String render(CountSpec spec) {
        return "SELECT COUNT(*) FROM %s".formatted(render(spec.table()));
    }

    public static String render(TruncateSpec spec) {
        StringBuilder sql = new StringBuilder("TRUNCATE ").append(render(spec.table()));
        if (spec.restartIdentity()) {
            sql.append(" RESTART IDENTITY");
        }
        if (spec.cascade()) {
            sql.append(" CASCADE");
        }
        return sql.toString();
    }

    private static String render(SqlIdentifier identifier) {
        return identifier.value();
    }

    private static String render(List<SqlIdentifier> identifiers) {
        return identifiers.stream()
                .map(SqlRenderer::render)
                .collect(Collectors.joining(", "));
    }

    private static String placeholders(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "?")
                .collect(Collectors.joining(", "));
    }
}
