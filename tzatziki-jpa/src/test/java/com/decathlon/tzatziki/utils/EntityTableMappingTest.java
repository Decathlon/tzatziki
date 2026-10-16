package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.app.model.Item;
import com.decathlon.tzatziki.app.model.Shipment;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTableMappingTest {

    private static EntityManagerFactory entityManagerFactory;

    @BeforeAll
    static void bootHibernateWithoutDatabase() {
        entityManagerFactory = Persistence.createEntityManagerFactory("mapping-pu", Map.of(
                "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.boot.allow_jdbc_metadata_access", "false",
                "hibernate.hbm2ddl.auto", "none"));
    }

    @AfterAll
    static void close() {
        entityManagerFactory.close();
    }

    @Test
    void resolvesSchemaTableAndBasicColumns() {
        EntityTableMapping mapping = EntityTableMapping.of(entityManagerFactory, Shipment.class);

        assertThat(mapping.schema()).isEqualTo("logistics");
        assertThat(mapping.table()).isEqualTo("shipments");
        assertThat(mapping.qualifiedTable()).isEqualTo("logistics.shipments");
        assertThat(mapping.propertyByColumn())
                .containsEntry("id", "id")
                .containsEntry("tracking_no", "trackingNumber")
                .doesNotContainKey("category_id");
    }

    @Test
    void unqualifiedTable() {
        EntityTableMapping mapping = EntityTableMapping.of(entityManagerFactory, Item.class);

        assertThat(mapping.schema()).isNull();
        assertThat(mapping.qualifiedTable()).isEqualTo("items");
    }

    @Test
    void translatesColumnNamesToPropertyNames() {
        EntityTableMapping mapping = new EntityTableMapping(null, "t", Map.of("tracking_no", "trackingNumber", "code", "label", "label", "other"));

        assertThat(mapping.toProperty("tracking_no")).isEqualTo("trackingNumber");
        assertThat(mapping.toProperty("trackingNumber")).isEqualTo("trackingNumber");
        // "label" is both a column and a property name: the property wins
        assertThat(mapping.toProperty("label")).isEqualTo("label");
        assertThat(mapping.toProperty("unknown")).isEqualTo("unknown");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("tracking_no", "ABC");
        row.put("other", null);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("id", 1);
        expected.put("trackingNumber", "ABC");
        expected.put("other", null);
        assertThat(mapping.withPropertyKeys(row)).containsExactlyEntriesOf(expected);
    }

    @Test
    void returnsNullForUnmanagedClassesAndNonHibernateFactories() {
        assertThat(EntityTableMapping.of(entityManagerFactory, String.class)).isNull();

        EntityManagerFactory nonHibernate = (EntityManagerFactory) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EntityManagerFactory.class}, (proxy, method, args) -> {
                    throw new PersistenceException("not hibernate");
                });
        assertThat(EntityTableMapping.of(nonHibernate, Item.class)).isNull();
    }
}
