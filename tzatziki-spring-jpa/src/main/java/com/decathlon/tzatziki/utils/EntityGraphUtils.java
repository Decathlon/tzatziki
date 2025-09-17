package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Subgraph;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

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

        // Add all attributes from expectedEntities to the entity graph
        if (!expectedEntities.isEmpty()) {
            Map firstEntity = expectedEntities.get(0);
            addAttributesToGraph(entityGraph, firstEntity);
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
    public static void addAttributesToGraph(Object graph, Map attributeMap) {
        attributeMap.forEach((key, value) -> {
            String attributeName = key.toString();

            if (value instanceof Map valueMap) {
                // Handle nested objects recursively
                try {
                    Subgraph<?> subGraph;
                    if (graph instanceof EntityGraph) {
                        subGraph = ((EntityGraph<?>) graph).addSubgraph(attributeName);
                    } else if (graph instanceof Subgraph) {
                        subGraph = ((Subgraph<?>) graph).addSubgraph(attributeName);
                    } else {
                        return; // Unsupported graph type
                    }

                    // Recursively add nested attributes
                    addAttributesToGraph(subGraph, valueMap);
                } catch (Exception e) {
                    // If subgraph creation fails, just add the attribute node
                    addSimpleAttribute(graph, attributeName);
                }
            } else {
                // Handle simple attributes
                addSimpleAttribute(graph, attributeName);
            }
        });
    }

    /**
     * Adds a simple attribute node to an EntityGraph or Subgraph.
     *
     * @param graph         the EntityGraph or Subgraph to add the attribute to
     * @param attributeName the name of the attribute to add
     */
    public static void addSimpleAttribute(Object graph, String attributeName) {
        try {
            if (graph instanceof EntityGraph) {
                ((EntityGraph<?>) graph).addAttributeNodes(attributeName);
            } else if (graph instanceof Subgraph) {
                ((Subgraph<?>) graph).addAttributeNodes(attributeName);
            }
        } catch (Exception e) {
            // Silently ignore if attribute cannot be added (e.g., doesn't exist in entity)
        }
    }
}
