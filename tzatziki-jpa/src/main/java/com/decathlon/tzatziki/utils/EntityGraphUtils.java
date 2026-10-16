package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.graph.Graph;
import org.hibernate.graph.SubGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EntityGraphUtils {
    /**
     * Creates an EntityGraph for the given entity class based on the expected entities attributes.
     * This helps optimize queries by only loading the necessary fields.
     *
     * @param entityManager    the EntityManager to create the graph with
     * @param entityClass      the entity class type
     * @param expectedEntities the expected entities containing the attributes to fetch
     * @return configured EntityGraph for the entity
     */
    public static <E> EntityGraph<E> createEntityGraph(EntityManager entityManager, Class<E> entityClass, List<Map> expectedEntities) {
        EntityGraph<E> entityGraph = entityManager.createEntityGraph(entityClass);

        if (entityGraph instanceof Graph<?> && !expectedEntities.isEmpty()) {
            @SuppressWarnings("unchecked")
            Graph<E> graph = (Graph<E>) entityGraph;
            addAttributesToGraph(graph, mergeShapes(expectedEntities));
        }

        return entityGraph;
    }

    /**
     * Deep-merges the structure of the given expected entities, so that attributes present in any of them are kept.
     * Lists of maps are merged into a single-element list holding the merged shape of their elements.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeShapes(Collection<? extends Map> maps) {
        Map<String, Object> shape = new LinkedHashMap<>();
        for (Map<String, Object> map : (Collection<Map<String, Object>>) maps) {
            if (map == null) {
                continue;
            }
            map.forEach((key, value) -> mergeValue(shape, key, value));
        }
        return shape;
    }

    private static void mergeValue(Map<String, Object> shape, String key, Object value) {
        Object existing = shape.get(key);
        if (value instanceof Map<?, ?> nested) {
            shape.put(key, mergeShapes(existing instanceof Map<?, ?> existingMap ? List.of(existingMap, nested) : List.of(nested)));
        } else if (value instanceof Collection<?> collection) {
            List<Map<?, ?>> elements = new ArrayList<>(mapsIn(existing));
            elements.addAll(mapsIn(collection));
            shape.put(key, elements.isEmpty() ? List.of() : List.of(mergeShapes(elements)));
        } else if (!(existing instanceof Map) && !(existing instanceof List)) {
            shape.put(key, value);
        }
    }

    private static List<Map<?, ?>> mapsIn(Object values) {
        if (!(values instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().filter(Map.class::isInstance).<Map<?, ?>>map(Map.class::cast).toList();
    }


    /**
     * Recursively adds attributes to an EntityGraph or Subgraph.
     */
    @SuppressWarnings("unchecked")
    public static <E> void addAttributesToGraph(Graph<E> graph, Map<String, Object> attributeMap) {
        attributeMap.forEach((attributeName, value) -> {
            if (value instanceof Map<?, ?> valueMap) {
                SubGraph<E> subGraph = graph.addSubGraph(attributeName);
                addAttributesToGraph(subGraph, (Map<String, Object>) valueMap);
            } else if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> elementMap) {
                SubGraph<E> subGraph = graph.addSubGraph(attributeName);
                addAttributesToGraph(subGraph, (Map<String, Object>) elementMap);
            } else {
                graph.addAttributeNode(attributeName);
            }
        });
    }
}
