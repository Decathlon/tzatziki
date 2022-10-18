package com.decathlon.tzatziki.init;

import com.decathlon.tzatziki.utils.MapperDelegate;

import java.lang.reflect.Type;
import java.util.List;

public class TestMapperDelegate implements MapperDelegate {
    @Override
    public <E> E read(String content) {
        return null;
    }

    @Override
    public <E> List<E> readAsAListOf(String content, Class<E> clazz) {
        return null;
    }

    @Override
    public <E> E read(String content, Class<E> clazz) {
        return null;
    }

    @Override
    public <E> E read(String content, Type type) {
        return null;
    }

    @Override
    public String toJson(Object object) {
        return null;
    }

    @Override
    public String toNonDefaultJson(Object object) {
        return null;
    }

    @Override
    public String toYaml(Object object) {
        return String.valueOf(object);
    }
}
