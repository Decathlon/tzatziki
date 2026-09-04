package com.decathlon.tzatziki.utils.sql;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record SqlIdentifier(String value) {

    private static final Pattern VALID_SQL_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");

    public SqlIdentifier {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SqlIdentifier of(String value, String context) {
        validate(value, context);
        return new SqlIdentifier(value);
    }

    public static SqlIdentifier schema(String value) {
        return of(value, "schema name");
    }

    public static SqlIdentifier table(String value) {
        return of(value, "table name");
    }

    public static SqlIdentifier column(String value) {
        return of(value, "column name");
    }

    public static List<SqlIdentifier> columns(Collection<String> values) {
        return values.stream().map(SqlIdentifier::column).toList();
    }

    public static void validate(String value, String context) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(context + " must not be null or empty");
        }
        if (!VALID_SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + context + ": '" + value + "'. Only letters, digits, underscores, and dots (for schema.table) are allowed.");
        }
    }
}
