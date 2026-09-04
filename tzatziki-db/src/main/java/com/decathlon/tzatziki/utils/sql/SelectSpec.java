package com.decathlon.tzatziki.utils.sql;

import java.util.Collection;
import java.util.List;

public record SelectSpec(SqlIdentifier table, List<SqlIdentifier> columns) {

    public SelectSpec {
        columns = List.copyOf(columns);
    }

    public static Builder from(String table) {
        return new Builder(SqlIdentifier.table(table));
    }

    public static final class Builder {
        private final SqlIdentifier table;

        private Builder(SqlIdentifier table) {
            this.table = table;
        }

        public SelectSpec allColumns() {
            return new SelectSpec(table, List.of());
        }

        public SelectSpec columns(Collection<String> columnNames) {
            return new SelectSpec(table, SqlIdentifier.columns(columnNames));
        }
    }
}
