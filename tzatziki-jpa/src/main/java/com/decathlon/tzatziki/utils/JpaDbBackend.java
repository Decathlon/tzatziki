package com.decathlon.tzatziki.utils;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Adapter that wraps a {@link JpaBackend} to implement the {@link DbBackend} interface.
 * <p>
 * When registered with {@link com.decathlon.tzatziki.steps.DatabaseSteps#registerBackend(DbBackend)},
 * table-level step definitions in DatabaseSteps will use JPA entity resolution,
 * entity graph optimization, and backend-owned JPA queries instead of raw JDBC.
 */
public class JpaDbBackend implements DbBackend {

    private final JpaBackend jpaBackend;

    public JpaDbBackend(JpaBackend jpaBackend) {
        this.jpaBackend = jpaBackend;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void insertRows(String table, List<Map<String, Object>> rows) {
        Class<Object> entityClass = (Class<Object>) resolveEntityClass(table);
        List<Object> entities = rows.stream()
                .map(row -> Mapper.read(Mapper.toJson(row), entityClass))
                .toList();
        jpaBackend.saveAll(entityClass, entities);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows) {
        Class<Object> entityClass = (Class<Object>) resolveEntityClass(table);
        List<Map> expectedMaps = expectedRows == null ? null : (List<Map>) (List<?>) expectedRows;
        List<?> entities = jpaBackend.findAllWithExpectedFields(entityClass, expectedMaps);
        // Serialize entities to maps, filtering to expected columns
        Set<String> columns = new LinkedHashSet<>();
        if (expectedRows != null) {
            expectedRows.forEach(row -> columns.addAll(row.keySet()));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object entity : entities) {
            String json = Mapper.toNonDefaultJson(entity);
            Map<String, Object> map = Mapper.read(json, Map.class);
            if (!columns.isEmpty()) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (String col : columns) {
                    if (map.containsKey(col)) {
                        filtered.put(col, map.get(col));
                    }
                }
                results.add(filtered);
            } else {
                results.add(map);
            }
        }
        return results;
    }

    @Override
    public long count(String table) {
        Class<?> entityClass = resolveEntityClass(table);
        return jpaBackend.count(entityClass);
    }

    @Override
    public void truncate(String table) {
        Class<?> entityClass = resolveEntityClass(table);
        jpaBackend.truncate(entityClass);
    }

    private Class<?> resolveEntityClass(String table) {
        Type type = jpaBackend.resolveEntityType(table);
        if (type instanceof Class<?> clazz) return clazz;
        throw new AssertionError("Cannot resolve entity class for table: " + table);
    }
}
