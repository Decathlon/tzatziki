package com.decathlon.tzatziki.utils;

import com.google.common.reflect.ClassPath;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.semla.reflect.Types.parameterized;
import static io.semla.reflect.Types.rawTypeOf;

@Slf4j
@SuppressWarnings("UnstableApiUsage")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TypeParser {

    private static final Pattern typePattern = Pattern.compile("((?:[a-z_$][a-z0-9_$]*\\.)*[A-Z_$][A-z0-9_$]*)(?:<(.*)>)?");
    private static List<ClassPath.ClassInfo> reflections;
    private static final Map<String, Type> KNOWN_TYPES = new LinkedHashMap<>();
    private static String defaultPackage = null;

    public static void setDefaultPackage(String defaultPackage) {
        KNOWN_TYPES.clear();
        reflections = null;
        TypeParser.defaultPackage = defaultPackage;
    }

    public static synchronized Type parse(String name) {
        if (name == null) {
            return null;
        }
        Matcher matcher = typePattern.matcher(name);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("illegal type format: " + name);
        }

        name = matcher.group(1);
        Type type = parseType(name);
        if (matcher.group(2) == null) {
            return type;
        } else {
            List<String> names = splitNames(matcher.group(2));
            Type parameter = parse(names.get(0));
            Type[] parameters = names.size() > 1
                    ? names.stream().skip(1).map(TypeParser::parse).toArray(Type[]::new)
                    : new Type[0];
            return parameterized(rawTypeOf(type)).of(parameter, parameters);
        }
    }

    @NotNull
    private static Type parseType(String name) {
        return KNOWN_TYPES.computeIfAbsent(name, n -> switch (n) {
            case "Map" -> Map.class;
            case "List" -> List.class;
            case "Set" -> Set.class;
            case "String" -> String.class;
            case "Integer" -> Integer.class;
            case "Double" -> Double.class;
            case "Long" -> Long.class;
            case "Boolean" -> Boolean.class;
            case "Instant" -> Instant.class;
            case "Number" -> Number.class;
            default -> classes()
                    .stream()
                    .filter(clazz -> {
                        String className = clazz.getName();
                        if (className == null) {
                            return false;
                        }
                        return className.equals(n) || clazz.getSimpleName().equals(n);
                    })
                    .findFirst()
                    .map((ClassPath.ClassInfo classInfo) -> (Type) classInfo.load())
                    .orElseGet(() -> {
                        try {
                            return Class.forName(n);
                        } catch (ClassNotFoundException e) {
                            throw new AssertionError(e);
                        }
                    });
        });
    }

    private static List<String> splitNames(String input) {
        int depth = 0;
        StringBuilder buffer = new StringBuilder();
        List<String> output = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '<' -> {
                    depth++;
                    buffer.append(c);
                }
                case '>' -> {
                    depth--;
                    buffer.append(c);
                }
                case ' ' -> {}
                default -> {
                    if (c == ',' && depth == 0) {
                        output.add(buffer.toString());
                        buffer = new StringBuilder();
                    } else {
                        buffer.append(c);
                    }
                }
            }
        }
        if (buffer.length() > 0) {
            output.add(buffer.toString());
        }
        return output;
    }

    @SneakyThrows
    public static synchronized List<ClassPath.ClassInfo> classes() {
        if (reflections == null) {
            reflections = ClassPath.from(ClassLoader.getSystemClassLoader()).getAllClasses()
                    .stream()
                    .sorted((class1, class2) -> {
                                if (defaultPackage != null) {
                                    if (class1.getPackageName().startsWith(defaultPackage)
                                            && !class2.getPackageName().startsWith(defaultPackage)) {
                                        return -1;
                                    } else if (!class1.getPackageName().startsWith(defaultPackage)
                                            && class2.getPackageName().startsWith(defaultPackage)) {
                                        return 1;
                                    }
                                }
                                return class1.getPackageName().compareTo(class2.getPackageName());
                            }
                    )
                    .collect(Collectors.toList());
        }
        return reflections;
    }
}
