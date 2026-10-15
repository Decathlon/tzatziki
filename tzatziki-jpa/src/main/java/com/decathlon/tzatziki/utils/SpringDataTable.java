package com.decathlon.tzatziki.utils;

import java.lang.annotation.Annotation;

/**
 * Spring Data relational {@code @Table}, resolved by name since spring-data is not a dependency of tzatziki-jpa.
 */
final class SpringDataTable {

    static final Class<? extends Annotation> ANNOTATION = load();

    private SpringDataTable() {
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> load() {
        try {
            return (Class<? extends Annotation>) Class.forName("org.springframework.data.relational.core.mapping.Table");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
