package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Mapper {
    private static boolean convertDotPropertiesToObject = true;

    public static void shouldConvertDotPropertiesToObject(boolean shouldConvertDotPropertiesToObject) {
        convertDotPropertiesToObject = shouldConvertDotPropertiesToObject;
    }

    private static final MapperDelegate delegate = ServiceLoader.load(MapperDelegate.class)
            .findFirst()
            .orElseThrow();

    public static <E> E read(String content) {
        content = toYaml(content);
        return delegate.read(content);
    }

    public static <E> List<E> readAsAListOf(String content, Class<E> clazz) {
        content = toYaml(content);
        if (clazz == Type.class) clazz = (Class<E>) Class.class;
        return delegate.readAsAListOf(content, clazz);
    }

    public static <E> E read(String content, Class<E> clazz) {
        if (clazz == Type.class) clazz = (Class<E>) Class.class;
        if (clazz == String.class) return (E) content;
        return read(content, (Type) clazz);
    }

    public static <E> E read(String content, Type type) {
        content = toYaml(content);
        return delegate.read(content, type);
    }

    public static String toJson(Object object) {
        return delegate.toJson(object);
    }

    public static String toNonDefaultJson(Object object) {
        return delegate.toNonDefaultJson(object);
    }

    public static String toYaml(Object object) {
        if (object instanceof String content && convertDotPropertiesToObject) {
            if (isList(content)) content = toYaml(delegate.read(content, List.class));
            else if (isJson(content)) content = toYaml(delegate.read(content, Map.class));
            content = dotNotationToYamlObject(content);
            object = content;
        }
        return delegate.toYaml(object);
    }

    private static String dotNotationToYamlObject(String content) {
        List<String> lines = content.lines().collect(Collectors.toList());

        for (int idx = 0; idx < lines.size(); idx++) {
            String line;
            String matchOnlyIfNonRegexFlag = "(?![ \"']*\\?e)";
            String captureDotNotation = "([^.]+)\\.([\\S ]+:" + matchOnlyIfNonRegexFlag + "[^\\n]*)\\n?";
            while ((line = lines.get(idx)).matches(captureDotNotation)) {
                String rootObjectIndent = Pattern.compile("([ \\-]*)[\\s\\S]*").matcher(line).replaceAll("$1").replace("-", " ");
                String subObjectIndent = "  "+rootObjectIndent;
                lines.set(idx, line.replaceAll(captureDotNotation, "$1:"));
                lines.add(idx + 1, line.replaceAll(captureDotNotation, subObjectIndent +"$2"));
                for (int subIdx = idx + 2; subIdx < lines.size() && lines.get(subIdx).startsWith(subObjectIndent); subIdx++) {
                    lines.set(subIdx, "  "+lines.get(subIdx));
                }
            }
        }

        return lines.stream().collect(Collectors.joining("\n"));
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
            if (charAt != ' ' && charAt != '\n') {
                return characters.contains(charAt);
            }
        }
        return false;
    }

}
