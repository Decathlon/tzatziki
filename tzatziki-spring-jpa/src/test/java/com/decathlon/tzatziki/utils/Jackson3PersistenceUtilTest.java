package com.decathlon.tzatziki.utils;

import jakarta.persistence.Transient;
import org.hibernate.collection.spi.PersistentCollection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Jackson3PersistenceUtilTest {

    public static class Entity {
        public String name = "DVador";
        @Transient
        public String secret = "hidden";
        public PersistentCollection<?> lazyItems;
    }

    @Test
    void skipsTransientFieldsAndUninitializedLazyCollections() {
        Jackson3PersistenceUtil.register();

        PersistentCollection<?> uninitialized = mock(PersistentCollection.class);
        when(uninitialized.wasInitialized()).thenReturn(false);

        Entity entity = new Entity();
        entity.lazyItems = uninitialized;

        String json = new Jackson3Mapper().toJson(entity);

        assertThat(json).contains("name").contains("DVador");
        assertThat(json).doesNotContain("secret").doesNotContain("hidden");
        assertThat(json).doesNotContain("lazyItems");
    }
}
