package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;

import java.util.*;

/**
 * Physical table of a JPA entity and the mapping of its basic columns to entity properties, as resolved by Hibernate
 * (so that naming strategies, inheritance and {@code @Column} names are taken into account).
 *
 * @param schema           the schema of the table, or null if unqualified
 * @param table            the unqualified table name
 * @param propertyByColumn single-column basic properties (including the identifier) indexed by column name
 */
public record EntityTableMapping(String schema, String table, Map<String, String> propertyByColumn) {

    public EntityTableMapping {
        Objects.requireNonNull(table, "table must not be null");
        propertyByColumn = Map.copyOf(propertyByColumn);
    }

    /**
     * @return the mapping of the given entity class in the given Hibernate EntityManagerFactory,
     * or null if the factory is not backed by Hibernate or does not manage this class
     */
    public static EntityTableMapping of(EntityManagerFactory entityManagerFactory, Class<?> entityClass) {
        EntityPersister persister;
        try {
            persister = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                    .getMappingMetamodel()
                    .getEntityDescriptor(entityClass);
        } catch (PersistenceException | IllegalArgumentException e) {
            return null;
        }
        String qualifiedTable = unquote(persister.getMappedTableDetails().getTableName());
        int dot = qualifiedTable.lastIndexOf('.');
        String schema = dot < 0 ? null : qualifiedTable.substring(0, dot);
        return new EntityTableMapping(schema, qualifiedTable.substring(dot + 1), propertyByColumn(persister));
    }

    private static Map<String, String> propertyByColumn(EntityPersister persister) {
        Map<String, String> propertyByColumn = new LinkedHashMap<>();
        if (persister instanceof AbstractEntityPersister entityPersister) {
            putSingleColumn(propertyByColumn, entityPersister.getIdentifierColumnNames(), entityPersister.getIdentifierPropertyName());
            String[] propertyNames = entityPersister.getPropertyNames();
            Type[] propertyTypes = entityPersister.getPropertyTypes();
            for (int i = 0; i < propertyNames.length; i++) {
                if (!propertyTypes[i].isAssociationType() && !propertyTypes[i].isComponentType()) {
                    putSingleColumn(propertyByColumn, entityPersister.getPropertyColumnNames(i), propertyNames[i]);
                }
            }
        }
        return propertyByColumn;
    }

    private static void putSingleColumn(Map<String, String> propertyByColumn, String[] columns, String property) {
        if (property != null && columns != null && columns.length == 1 && columns[0] != null) {
            propertyByColumn.putIfAbsent(unquote(columns[0]), property);
        }
    }

    private static String unquote(String identifier) {
        return identifier.replace("\"", "").replace("`", "");
    }

    public String qualifiedTable() {
        return schema == null ? table : schema + "." + table;
    }

    /**
     * @return the property name for the given key if it is a mapped column name and not already a property name,
     * otherwise the key itself
     */
    public String toProperty(String key) {
        if (propertyByColumn.containsValue(key)) return key;
        return propertyByColumn.getOrDefault(key, key);
    }

    /**
     * @return a copy of the given row where the column-name keys are replaced by their property names
     */
    public Map<String, Object> withPropertyKeys(Map<String, Object> row) {
        Map<String, Object> translated = new LinkedHashMap<>();
        row.forEach((key, value) -> translated.put(toProperty(key), value));
        return translated;
    }
}
