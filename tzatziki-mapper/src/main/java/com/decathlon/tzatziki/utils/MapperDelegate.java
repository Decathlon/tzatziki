package com.decathlon.tzatziki.utils;

import java.lang.reflect.Type;
import java.util.List;

public interface MapperDelegate {

    <E> E read(String content);

    <E> List<E> readAsAListOf(String content, Class<E> clazz);

    <E> E read(String content, Class<E> clazz);

    <E> E read(String content, Type type);

    String toJson(Object object);

    String toNonDefaultJson(Object object);

    String toYaml(Object object);
}
