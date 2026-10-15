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

import java.util.Collection;
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
            Map<String, Object> expectedShape = EntityGraphUtils.mergeShapes(expectedEntities);
            for (E entity : results) {
                initializeNestedAssociations(entity, expectedShape);
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
        expectedMap.forEach((name, expected) -> {
            if (expected instanceof Map<?, ?> || expected instanceof List<?>) {
                try {
                    initializeAssociation(getFieldValue(entity, name), expected);
                } catch (Exception e) {
                    log.debug("Could not initialize association {} on {}", name, entity.getClass().getSimpleName(), e);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void initializeAssociation(Object association, Object expected) {
        if (association == null) {
            return;
        }
        Hibernate.initialize(association);
        if (expected instanceof Map<?, ?> nestedMap) {
            initializeNestedAssociations(association, (Map<String, Object>) nestedMap);
        } else if (association instanceof Collection<?> elements
                && expected instanceof List<?> expectedList
                && !expectedList.isEmpty()
                && expectedList.get(0) instanceof Map<?, ?> elementShape) {
            elements.forEach(element -> initializeNestedAssociations(element, (Map<String, Object>) elementShape));
        }
    }

    private static Object getFieldValue(Object entity, String fieldName) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            return entity.getClass().getMethod(getterName).invoke(entity);
        } catch (ReflectiveOperationException e) {
            return Fields.hasField(entity, fieldName) ? Fields.getValue(entity, fieldName) : null;
        }
    }
}
