package com.decathlon.tzatziki.utils.sql;

import java.util.Collection;
import java.util.List;

public record InsertSpec(SqlIdentifier table, List<SqlIdentifier> columns) {

    public InsertSpec {
        columns = List.copyOf(columns);
    }

    public static Builder into(String table) {
        return new Builder(SqlIdentifier.table(table));
    }

    public static final class Builder {
        private final SqlIdentifier table;

        private Builder(SqlIdentifier table) {
            this.table = table;
        }

        public InsertSpec columns(Collection<String> columnNames) {
            return new InsertSpec(table, SqlIdentifier.columns(columnNames));
        }
    }
}
