package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Mapper {
    private static boolean convertDotPropertiesToObject = true;

    public static void shouldConvertDotPropertiesToObject(boolean shouldConvertDotPropertiesToObject) {
        convertDotPropertiesToObject = shouldConvertDotPropertiesToObject;
    }

    private static final MapperDelegate delegate = selectDelegate();

    public static String activeDelegateName() {
        return delegate.getClass().getSimpleName();
    }

    private static MapperDelegate selectDelegate() {
        List<MapperDelegate> delegates = ServiceLoader.load(MapperDelegate.class).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());
        if (delegates.isEmpty()) {
            throw new IllegalStateException("No " + MapperDelegate.class.getName() + " implementation found on the classpath");
        }
        String requested = delegateSelector();
        if (requested != null && !requested.isBlank()) {
            return delegates.stream()
                    .filter(d -> matchesSelector(d, requested))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No MapperDelegate matching '" + requested + "' found. Available: "
                                    + delegates.stream().map(d -> d.getClass().getName()).collect(Collectors.joining(", "))));
        }
        if (delegates.size() == 1) {
            return delegates.get(0);
        }
        return delegates.stream()
                .filter(d -> d.getClass().getName().endsWith(".JacksonMapper"))
                .findFirst()
                .orElseGet(() -> delegates.get(0));
    }

    private static String delegateSelector() {
        String property = System.getProperty("tzatziki.mapper.delegate");
        if (property == null || property.isBlank()) {
            property = System.getenv("TZATZIKI_MAPPER_DELEGATE");
        }
        return property;
    }

    private static boolean matchesSelector(MapperDelegate delegate, String selector) {
        String className = delegate.getClass().getName();
        if (className.equalsIgnoreCase(selector)) {
            return true;
        }
        String simpleName = delegate.getClass().getSimpleName();
        return ("jackson2".equalsIgnoreCase(selector) && "JacksonMapper".equals(simpleName))
                || ("jackson3".equalsIgnoreCase(selector) && "Jackson3Mapper".equals(simpleName));
    }

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
            String captureDotNotation = "(?>([ \\-]*))" + matchOnlyIfNonRegexFlag + "([^.:]+)\\.((?>[^:]+)(?<!\\d{4}-\\d{2}-\\d{2}T\\d{2}):" + matchOnlyIfNonRegexFlag + ".*)";
            while ((line = lines.get(idx)).matches(captureDotNotation)) {
                String rootObjectIndent = line.replaceAll(captureDotNotation, "$1").replace("-", " ");
                String subObjectIndent = "  " + rootObjectIndent;
                lines.set(idx, line.replaceAll(captureDotNotation, "$1$2:"));
                // We don't need Sonar to check this line as the input data is trusted (it's coming from the feature file itself)
                lines.add(idx + 1, line.replaceAll(captureDotNotation, subObjectIndent + "$3")); // NOSONAR
                for (int subIdx = idx + 2; subIdx < lines.size() && (lines.get(subIdx).startsWith(subObjectIndent) || lines.get(subIdx).startsWith(rootObjectIndent + "-")); subIdx++) {
                    lines.set(subIdx, "  " + lines.get(subIdx));
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
