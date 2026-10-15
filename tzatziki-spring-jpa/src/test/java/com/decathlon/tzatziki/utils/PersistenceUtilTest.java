package com.decathlon.tzatziki.utils;

import jakarta.persistence.Transient;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistenceUtilTest {

    public static class Entity {
        public String name = "DVador";
        @Transient
        public String secret = "hidden";
        @org.springframework.data.annotation.Transient
        public String springSecret = "hidden too";
        public PersistentCollection<?> lazyItems;
        public Object lazyParent;
    }

    @Test
    void skipsTransientFieldsAndUninitializedLazyAssociations() {
        PersistenceUtil.registerTransientAnnotation(org.springframework.data.annotation.Transient.class);
        PersistenceUtil.registerTransientAnnotation(org.springframework.data.annotation.Transient.class);
        JacksonMapper.with(objectMapper -> objectMapper.registerModule(PersistenceUtil.getMapperModule()));

        PersistentCollection<?> uninitializedCollection = mock(PersistentCollection.class);
        when(uninitializedCollection.wasInitialized()).thenReturn(false);
        LazyInitializer lazyInitializer = mock(LazyInitializer.class);
        when(lazyInitializer.isUninitialized()).thenReturn(true);
        HibernateProxy uninitializedProxy = mock(HibernateProxy.class);
        when(uninitializedProxy.getHibernateLazyInitializer()).thenReturn(lazyInitializer);
        when(uninitializedProxy.asHibernateProxy()).thenReturn(uninitializedProxy);

        Entity entity = new Entity();
        entity.lazyItems = uninitializedCollection;
        entity.lazyParent = uninitializedProxy;

        String json = new JacksonMapper().toJson(entity);

        assertThat(json)
                .contains("name", "DVador")
                .doesNotContain("secret", "hidden", "springSecret", "lazyItems", "lazyParent");
    }

    @Test
    void resolvesPersistenceClassesByName() {
        assertThat(PersistenceUtil.<Object>getPersistenceClass("Entity")).isEqualTo(jakarta.persistence.Entity.class);
    }
}
