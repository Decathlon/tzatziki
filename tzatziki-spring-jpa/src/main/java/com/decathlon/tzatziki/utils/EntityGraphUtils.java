package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.graph.Graph;
import org.hibernate.graph.SubGraph;

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
    @SuppressWarnings("java:S3740") // Suppress unchecked cast warning
    public static <E> EntityGraph<E> createEntityGraph(EntityManager entityManager, Class<E> entityClass, List<Map> expectedEntities) {
        EntityGraph<E> entityGraph = entityManager.createEntityGraph(entityClass);

        // Add all attributes from expectedEntities to the entity graph
        if (entityGraph instanceof Graph<?> && !expectedEntities.isEmpty()) {
            @SuppressWarnings("unchecked")
            Graph<E> graph = (Graph<E>) entityGraph;
            Map<String, Object> firstEntity = expectedEntities.get(0);
            addAttributesToGraph(graph, firstEntity);
        }

        return entityGraph;
    }


    /**
     * Recursively adds attributes to an EntityGraph or Subgraph.
     * This method handles nested objects at any depth.
     *
     * @param graph        the EntityGraph or Subgraph to add attributes to
     * @param attributeMap the map containing attributes to add
     */
    @SuppressWarnings("java:S3740") // Suppress unchecked cast warning
    public static <E> void addAttributesToGraph(Graph<E> graph, Map<String, Object> attributeMap) {
        attributeMap.forEach((key, value) -> {
            String attributeName = key;
            if (value instanceof Map valueMap) {
                // Handle nested objects recursively
                SubGraph<E> subGraph = graph.addSubGraph(attributeName);
                addAttributesToGraph(subGraph, valueMap);
            } else {
                // Handle simple attributes
                graph.addAttributeNode(attributeName);
            }
        });
    }
}
