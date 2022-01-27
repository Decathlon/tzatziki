package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * @author based on https://blog.sebastian-daschner.com/entries/changing_env_java
 */
@SuppressWarnings("unchecked")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Env {

    /**
     * inject an environment variable at runtime
     *
     * @return the previous value
     */
    @SneakyThrows
    public static String export(String key, String value) {
            String current = System.getenv("key");
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            Field theUnmodifiableEnvironment = Fields.getField(processEnvironment, "theUnmodifiableEnvironment");

            Map<String, String> unmodifiableMap = (Map<String, String>) theUnmodifiableEnvironment.get(null);
            Fields.<Map<String, String>>getValue(unmodifiableMap, "m").put(key, value);

            Map<Object, Object> map = (Map<Object, Object>) Fields.getField(processEnvironment, "theEnvironment").get(null);
            Object variable = Methods.invoke(Class.forName("java.lang.ProcessEnvironment$Variable"), "valueOfQueryOnly", key);
            Object valueWrapped = Methods.invoke(Class.forName("java.lang.ProcessEnvironment$Value"), "valueOfQueryOnly", value);
            map.put(variable, valueWrapped);
            return current;
    }
}
