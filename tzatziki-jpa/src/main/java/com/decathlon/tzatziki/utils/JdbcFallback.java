package com.decathlon.tzatziki.utils;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC backend used by {@link JpaBackend} for tables without JPA entity (join tables, audit tables...).
 */
@Slf4j
final class JdbcFallback {

    private static final Set<String> WARNED_TABLES = ConcurrentHashMap.newKeySet();

    private JdbcFallback() {
    }

    static DbBackend forTable(List<DataSource> dataSources, String table) {
        if (dataSources.isEmpty()) {
            throw new AssertionError("Cannot resolve entity class for table: " + table);
        }
        if (WARNED_TABLES.add(table)) {
            log.warn("No JPA entity is mapped to the '{}' table: falling back to plain JDBC on the first DataSource "
                    + "(rows use column names and no entity listener or conversion is applied)", table);
        }
        return new JdbcBackend(dataSources.get(0));
    }
}
