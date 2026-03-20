package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import tools.jackson.dataformat.yaml.YAMLReadFeature;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class JacksonMapper implements MapperDelegate {

    private static final UnaryOperator<ObjectMapper> configurator = objectMapper -> objectMapper.rebuild()
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static ObjectMapper yaml = configurator.apply(new ObjectMapper(YAMLFactory.builder().enable(YAMLReadFeature.EMPTY_STRING_AS_NULL).disable(YAMLWriteFeature.SPLIT_LINES).build()));
    private static ObjectMapper json = configurator.apply(new ObjectMapper());
    private static ObjectMapper nonDefaultJson = json.rebuild()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_DEFAULT))
            .build();


    public static void with(UnaryOperator<ObjectMapper> configurator) {
        yaml = configurator.apply(yaml);
        json = configurator.apply(json);
        nonDefaultJson = configurator.apply(nonDefaultJson);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <E> E read(String content) {
        if (Mapper.isList(content)) {
            return (E) yaml.readValue(content, List.class);
        }
        return (E) yaml.readValue(content, Map.class);
    }

    @SneakyThrows
    public <E> List<E> readAsAListOf(String content, Class<E> clazz) {
        if (Mapper.isList(content)) {
            return yaml.readValue(content, yaml.getTypeFactory().constructParametricType(List.class, clazz));
        }
        try {
            return Lists.newArrayList(yaml.readValue(content, clazz));
        } catch (JacksonException e) {
            return readAsAListOf("[%s]".formatted(content), clazz);
        }
    }

    @SneakyThrows
    public <E> E read(String content, Class<E> clazz) {
        return yaml.readValue(content, clazz);
    }

    @SneakyThrows
    public <E> E read(String content, Type type) {
        return yaml.readValue(content, toJavaType(type));
    }

    private static JavaType toJavaType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            JavaType[] javaTypes = Stream.of(parameterizedType.getActualTypeArguments()).map(JacksonMapper::toJavaType).toArray(JavaType[]::new);
            return yaml.getTypeFactory().constructParametricType((Class<?>) ((ParameterizedType) type).getRawType(), javaTypes);
        }
        return yaml.getTypeFactory().constructType(type);
    }

    @SneakyThrows
    public String toJson(Object object) {
        return toJson(object, json);
    }

    @SneakyThrows
    public String toNonDefaultJson(Object object) {
        return toJson(object, nonDefaultJson);
    }

    private static String toJson(Object object, ObjectMapper objectMapper) throws JacksonException {
        if (object instanceof String string) {
            try {
                if (Mapper.isJson(string)) {
                    return string;
                }
                if (Mapper.isList(string)) {
                    return Mapper.toJson(Mapper.read(string, List.class));
                }
                return Mapper.toJson(Mapper.read(string, Map.class));
            } catch (Exception e) {
                return string;
            }
        }

        String value = objectMapper.writeValueAsString(object);
        if (value.charAt(0) == '\"' && value.charAt(value.length() - 1) == '\"') {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    @SneakyThrows
    public String toYaml(Object object) {
        if (object instanceof String objectStr) {
            return objectStr;
        }
        return yaml.writeValueAsString(object).replaceAll("^---\n?", "");
    }
}
