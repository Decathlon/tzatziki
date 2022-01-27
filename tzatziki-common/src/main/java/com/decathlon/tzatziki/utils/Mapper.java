package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Mapper {

    private static final UnaryOperator<ObjectMapper> configurator = objectMapper -> objectMapper
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

    private static ObjectMapper yaml = configurator.apply(new ObjectMapper(new YAMLFactory()));
    private static ObjectMapper json = configurator.apply(new ObjectMapper());
    private static ObjectMapper nonDefaultJson = json.copy()
            .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);


    public static void with(UnaryOperator<ObjectMapper> configurator) {
        yaml = configurator.apply(yaml);
        json = configurator.apply(json);
        nonDefaultJson = configurator.apply(nonDefaultJson);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <E> E read(String content) {
        if (isList(content)) {
            return (E) yaml.readValue(content, List.class);
        }
        return (E) yaml.readValue(content, Map.class);
    }

    @SneakyThrows
    public static <E> List<E> readAsAListOf(String content, Class<E> clazz) {
        if (Mapper.isList(content)) {
            return yaml.readValue(content, yaml.getTypeFactory().constructParametricType(List.class, clazz));
        }
        try {
            return Lists.newArrayList(yaml.readValue(content, clazz));
        } catch (JsonProcessingException e) {
            return readAsAListOf("[%s]".formatted(content), clazz);
        }
    }

    @SneakyThrows
    public static <E> E read(String content, Class<E> clazz) {
        return yaml.readValue(content, clazz);
    }

    @SneakyThrows
    public static <E> E read(String content, Type type) {
        return yaml.readValue(content, toJavaType(type));
    }

    private static JavaType toJavaType(Type type) {
        if (type instanceof ParameterizedType) {
            JavaType[] javaTypes = Stream.of(((ParameterizedType) type).getActualTypeArguments()).map(Mapper::toJavaType).toArray(JavaType[]::new);
            return yaml.getTypeFactory().constructParametricType((Class<?>) ((ParameterizedType) type).getRawType(), javaTypes);
        }
        return yaml.getTypeFactory().constructType(type);
    }

    @SneakyThrows
    public static String toJson(Object object) {
        return toJson(object, json);
    }

    @SneakyThrows
    public static String toNonDefaultJson(Object object) {
        return toJson(object, nonDefaultJson);
    }

    private static String toJson(Object object, ObjectMapper objectMapper) throws JsonProcessingException {
        if (object instanceof String) {
            try {
                if (Mapper.isJson((String) object)) {
                    return (String) object;
                }
                if (Mapper.isList((String) object)) {
                    return Mapper.toJson(Mapper.read((String) object, List.class));
                }
                return Mapper.toJson(Mapper.read((String) object, Map.class));
            } catch (Exception e) {
                return (String) object;
            }
        }

        String value = objectMapper.writeValueAsString(object);
        if (value.charAt(0) == '\"' && value.charAt(value.length() - 1) == '\"') {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    @SneakyThrows
    public static String toYaml(Object object) {
        if (object instanceof String) {
            return (String) object;
        }
        return yaml.writeValueAsString(object).replaceAll("^---\n?", "");
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
