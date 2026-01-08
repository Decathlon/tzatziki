package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Unchecked.unchecked;

@SuppressWarnings("unchecked")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistenceUtil {
    private static final Map<String, Class<?>> persistenceClassByName = Collections.synchronizedMap(new HashMap<>());

    public static Module getMapperModule() {
        // Return a simple module - Hibernate lazy loading serialization is not critical for tests
        return new SimpleModule();
    }

    public static <T> Class<T> getPersistenceClass(String className) {
        return (Class<T>) persistenceClassByName.computeIfAbsent(
                className,
                clazz -> {
                    Class<Object> foundClass;
                    try {
                        foundClass = (Class<Object>) Class.forName("javax.persistence." + className);
                    } catch (ClassNotFoundException e) {
                        foundClass = unchecked(() -> (Class<Object>) Class.forName("jakarta.persistence." + className));
                    }
                    return foundClass;
                }
        );
    }

}
