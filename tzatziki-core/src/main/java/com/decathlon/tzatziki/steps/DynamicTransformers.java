package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.DefaultParameterTransformer;
import io.semla.reflect.Types;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static io.semla.reflect.Types.rawTypeOf;

public class DynamicTransformers {

    private static final Map<Type, Function<String, Object>> PARAMETER_TRANSFORMERS = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public static <E> void register(Class<E> type, Function<String, E> method) {
        PARAMETER_TRANSFORMERS.put(type, (Function<String, Object>) method);
    }

    @DefaultParameterTransformer
    public Object defaultTransformer(Object fromValue, Type toValueType) {
        if (toValueType.equals(String.class)) {
            return fromValue;
        }
        return PARAMETER_TRANSFORMERS.getOrDefault(toValueType, v -> {
            Class<Object> clazz = rawTypeOf(toValueType);
            if (v != null && !Types.isEqualToOneOf(clazz, Object.class, String.class)) {
                return Mapper.read(v, clazz);
            }
            return v;
        }).apply((String) fromValue);
    }
}
