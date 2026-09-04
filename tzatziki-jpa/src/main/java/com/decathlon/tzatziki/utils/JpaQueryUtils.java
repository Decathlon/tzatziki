package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.jpa.SpecHints;

import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JpaQueryUtils {

    /**
     * Query all entities, applying an entity graph derived from the expected assertion structure
     * when nested fields are requested.
     */
    public static <E> List<E> findAll(EntityManager entityManager, Class<E> entityClass, List<Map> expectedEntities) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<E> query = cb.createQuery(entityClass);
        Root<E> root = query.from(entityClass);
        query.select(root);

        TypedQuery<E> typedQuery = entityManager.createQuery(query);

        if (expectedEntities != null && !expectedEntities.isEmpty()) {
            try {
                EntityGraph<E> entityGraph = EntityGraphUtils.createEntityGraph(entityManager, entityClass, expectedEntities);
                typedQuery.setHint(SpecHints.HINT_SPEC_FETCH_GRAPH, entityGraph);
            } catch (Exception e) {
                log.debug("Could not create entity graph for {}, loading all fields", entityClass, e);
            }
        }

        List<E> results = typedQuery.getResultList();

        if (expectedEntities != null && !expectedEntities.isEmpty()) {
            Map<String, Object> firstExpected = expectedEntities.get(0);
            for (E entity : results) {
                initializeNestedAssociations(entity, firstExpected);
            }
        }

        return results;
    }

    /**
     * Recursively initializes lazy associations that are part of the expected structure
     * while the persistence context is still open.
     */
    private static void initializeNestedAssociations(Object entity, Map<String, Object> expectedMap) {
        if (entity == null || expectedMap == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : expectedMap.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nestedMap) {
                try {
                    Object associationValue = getFieldValue(entity, entry.getKey());
                    if (associationValue != null) {
                        Hibernate.initialize(associationValue);
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

    private static Object getFieldValue(Object entity, String fieldName) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            return entity.getClass().getMethod(getterName).invoke(entity);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(entity);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
