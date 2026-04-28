package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.jpa.SpecHints;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Adapter that wraps a {@link JpaBackend} to implement the {@link DbBackend} interface.
 * <p>
 * When registered with {@link com.decathlon.tzatziki.steps.DatabaseSteps#registerBackend(DbBackend)},
 * table-level step definitions in DatabaseSteps will use JPA entity resolution,
 * entity graph optimization, and the EntityManager for operations instead of raw JDBC.
 */
@Slf4j
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
        Class<?> entityClass = resolveEntityClass(table);
        List<?> entities = findAllWithEntityGraph(entityClass, expectedRows);
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

    /**
     * Query all entities using an entity graph built from the expected rows structure.
     * This ensures lazy-loaded relationships referenced in assertions are eagerly fetched.
     */
    @SuppressWarnings("unchecked")
    private <E> List<E> findAllWithEntityGraph(Class<?> entityClass, List<Map<String, Object>> expectedRows) {
        Class<E> clazz = (Class<E>) entityClass;
        EntityManager em = jpaBackend.getEntityManager(clazz);
        if (em == null) {
            return (List<E>) jpaBackend.findAll(clazz);
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> query = cb.createQuery(clazz);
        Root<E> root = query.from(clazz);
        query.select(root);

        TypedQuery<E> typedQuery = em.createQuery(query);

        if (expectedRows != null && !expectedRows.isEmpty()) {
            try {
                List<Map> expectedMaps = (List<Map>) (List<?>) expectedRows;
                EntityGraph<E> entityGraph = EntityGraphUtils.createEntityGraph(em, clazz, expectedMaps);
                typedQuery.setHint(SpecHints.HINT_SPEC_FETCH_GRAPH, entityGraph);
            } catch (Exception e) {
                log.debug("Could not create entity graph for {}, loading all fields", clazz, e);
            }
        }

        List<E> results = typedQuery.getResultList();

        // Force-initialize nested lazy associations referenced in expected data
        if (expectedRows != null && !expectedRows.isEmpty()) {
            Map<String, Object> firstExpected = expectedRows.get(0);
            for (E entity : results) {
                initializeNestedAssociations(entity, firstExpected);
            }
        }

        return results;
    }

    /**
     * Recursively initializes lazy associations on an entity that match nested map keys in expected data.
     */
    private void initializeNestedAssociations(Object entity, Map<String, Object> expectedMap) {
        if (entity == null || expectedMap == null) return;
        for (Map.Entry<String, Object> entry : expectedMap.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nestedMap) {
                try {
                    // Force-initialize the association
                    Object associationValue = getFieldValue(entity, entry.getKey());
                    if (associationValue != null) {
                        org.hibernate.Hibernate.initialize(associationValue);
                        // Recursively initialize deeper nesting
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nestedExpected = (Map<String, Object>) nestedMap;
                        initializeNestedAssociations(associationValue, nestedExpected);
                    }
                } catch (Exception e) {
                    log.debug("Could not initialize association {} on {}", entry.getKey(), entity.getClass().getSimpleName(), e);
                }
            }
        }
    }

    private Object getFieldValue(Object entity, String fieldName) {
        try {
            // Try getter method first
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            return entity.getClass().getMethod(getterName).invoke(entity);
        } catch (Exception e) {
            try {
                // Fall back to direct field access
                java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(entity);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
