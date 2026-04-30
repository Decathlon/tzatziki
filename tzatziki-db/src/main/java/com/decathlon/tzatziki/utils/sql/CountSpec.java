package com.decathlon.tzatziki.utils.sql;

public record CountSpec(SqlIdentifier table) {

    public static CountSpec from(String table) {
        return new CountSpec(SqlIdentifier.table(table));
    }
}
