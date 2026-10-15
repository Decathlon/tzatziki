package com.decathlon.tzatziki.utils;

import jakarta.persistence.Transient;
import org.hibernate.collection.spi.PersistentBag;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceUtilTest {

    @Retention(RetentionPolicy.RUNTIME)
    @interface CustomTransient {
    }

    public static class Entity {
        public String name = "DVador";
        @Transient
        public String secret = "hidden";
        @CustomTransient
        public String customSecret = "hidden too";
        public List<?> lazyItems = new PersistentBag<>();
        public Object lazyParent = uninitializedProxy();
    }

    @Test
    void skipsTransientFieldsAndUninitializedLazyAssociations() {
        PersistenceUtil.registerTransientAnnotation(CustomTransient.class);
        PersistenceUtil.registerTransientAnnotation(CustomTransient.class);
        JacksonMapper.with(objectMapper -> objectMapper.registerModule(PersistenceUtil.getMapperModule()));

        String json = new JacksonMapper().toJson(new Entity());

        assertThat(json)
                .contains("name", "DVador")
                .doesNotContain("secret", "hidden", "customSecret", "lazyItems", "lazyParent");
    }

    @Test
    void resolvesPersistenceClassesByName() {
        assertThat(PersistenceUtil.<Object>getPersistenceClass("Entity")).isEqualTo(jakarta.persistence.Entity.class);
    }

    private static Object uninitializedProxy() {
        LazyInitializer lazyInitializer = (LazyInitializer) Proxy.newProxyInstance(
                LazyInitializer.class.getClassLoader(), new Class<?>[]{LazyInitializer.class},
                (proxy, method, args) -> method.getName().equals("isUninitialized") ? Boolean.TRUE : null);
        return Proxy.newProxyInstance(
                HibernateProxy.class.getClassLoader(), new Class<?>[]{HibernateProxy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHibernateLazyInitializer" -> lazyInitializer;
                    case "asHibernateProxy" -> proxy;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
