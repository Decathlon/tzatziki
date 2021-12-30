package com.decathlon.tzatziki.utils;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.semla.reflect.Types.parameterized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class TypeParserTest {

    public static List<Arguments> parameters() {
        return List.of(
                // type aliases
                arguments("String", String.class),
                arguments("Map", Map.class),
                arguments("Set", Set.class),
                arguments("List", List.class),
                arguments("Integer", Integer.class),
                arguments("Double", Double.class),
                arguments("Long", Long.class),
                arguments("Boolean", Boolean.class),
                arguments("String", String.class),
                arguments("Number", Number.class),
                // real type by SimpleName
                arguments("TypeParser", TypeParser.class),
                // real type by FQN
                arguments("com.decathlon.tzatziki.utils.TypeParser", TypeParser.class),
                // real parameterized types
                arguments("List<String>", parameterized(List.class).of(String.class)),
                arguments("Map<String, List<Integer>>", parameterized(Map.class).of(String.class, parameterized(List.class).of(Integer.class)))
        );
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testParsing(String name, Type type) {
        assertThat(TypeParser.parse(name).getTypeName()).isEqualTo(type.getTypeName());
    }

    @Test
    public void testDefaultPackageName() {
        TypeParser.setDefaultPackage("com.fasterxml");
        assertThat(TypeParser.parse("TypeParser")).isEqualTo(com.fasterxml.jackson.databind.type.TypeParser.class);
        TypeParser.setDefaultPackage(null);
        assertThat(TypeParser.parse("TypeParser")).isEqualTo(TypeParser.class);
    }


}
