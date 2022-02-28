package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.reflect.Type;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Mapper {

    private static final MapperDelegate delegate = ServiceLoader.load(MapperDelegate.class)
            .findFirst()
            .orElseThrow();

    public static <E> E read(String content) {
        return delegate.read(content);
    }

    public static <E> List<E> readAsAListOf(String content, Class<E> clazz) {
        return delegate.readAsAListOf(content, clazz);
    }

    public static <E> E read(String content, Class<E> clazz) {
        return delegate.read(content, clazz);
    }

    public static <E> E read(String content, Type type) {
        return delegate.read(content, type);
    }

    public static String toJson(Object object) {
        return delegate.toJson(object);
    }

    public static String toNonDefaultJson(Object object) {
        return delegate.toNonDefaultJson(object);
    }

    public static String toYaml(Object object) {
        return delegate.toYaml(object);
    }

    public static boolean isJson(String value) {
        return firstNonWhitespaceCharacterIs(value, '{', '[');
    }

    public static boolean isList(String content) {
        return firstNonWhitespaceCharacterIs(content, '[', '-');
    }

    public static boolean firstNonWhitespaceCharacterIs(String text, Character... c) {
        Set<Character> characters = Set.of(c);
        for (int i = 0; i < text.length(); i++) {
            char charAt = text.charAt(i);
            if (charAt != ' ') {
                return characters.contains(charAt);
            }
        }
        return false;
    }

}
