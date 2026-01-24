package com.decathlon.tzatziki.utils;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.decathlon.tzatziki.utils.Unchecked.rethrow;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static java.util.Collections.synchronizedMap;
import static org.burningwave.core.assembler.StaticComponentContainer.Modules;


@SuppressWarnings({
    "unchecked",
    "java:S3011" // Allow reflective access to fields
})
@Slf4j
public final class Fields {

    private static final Map<Class<?>, Map<String, Field>> CACHE = synchronizedMap(new HashMap<>());

    private Fields() {
    }

    public static <E> void copyValue(E host, String name, E other) {
        copyValue(host, getField(host.getClass(), name), other);
    }

    public static <E> void copyValue(E host, Field field, E other) {
        setValue(other, field, getValue(host, field));
    }

    public static void setValue(Object host, String name, Object value) {
        setValue(host, getField(host.getClass(), name), value);
    }

    public static void setValue(Object host, Field field, Object value) {
        unchecked(() -> field.set(host, value));
    }

    public static <E> E getValue(Object host, String name) {
        return getValue(host, getField(host.getClass(), name));
    }

    public static <E> E getValue(Object host, Field field) {
        try {
            return (E) field.get(host);
        } catch (IllegalAccessException e) {
            return rethrow(e);
        }
    }

    public static Field getField(Class<?> clazz, String name) {
        return byName(clazz).get(name);
    }

    public static Map<String, Field> byName(Class<?> clazz) {
        return CACHE.computeIfAbsent(clazz, k -> recursivelyCacheFieldsOf(clazz));
    }

    public static <E> Stream<Field> of(E instance) {
        return of(instance.getClass());
    }

    public static Stream<Field> of(Class<?> clazz) {
        return byName(clazz).values().stream();
    }

    private static synchronized Map<String, Field> recursivelyCacheFieldsOf(Class<?> clazz) {
        Map<String, Field> fields = synchronizedMap(new LinkedHashMap<>());
        recursivelyCacheFieldsOf(clazz, fields);
        return fields;
    }

    private static void recursivelyCacheFieldsOf(Class<?> clazz, Map<String, Field> fields) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.getName().equals("$jacocoData") && !fields.containsKey(field.getName())) {
                try {
                    field.setAccessible(true);
                } catch (Exception e) {
                    log.debug("auto exporting {}!", field.getType().getPackageName());
                    Modules.exportPackageToAllUnnamed("java.base", field.getType().getPackageName());
                    field.setAccessible(true);
                }
                fields.put(field.getName(), field);
            }
        }
        if (clazz.getSuperclass() != null) {
            recursivelyCacheFieldsOf(clazz.getSuperclass(), fields);
        }
    }

    public static boolean hasField(Object host, String name) {
        return byName(host.getClass()).containsKey(name);
    }

    public static boolean hasField(Class<?> clazz, String name) {
        return byName(clazz).containsKey(name);
    }


}
