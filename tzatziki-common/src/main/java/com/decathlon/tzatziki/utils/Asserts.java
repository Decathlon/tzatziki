package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ThrowingRunnable;
import org.junit.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Double.parseDouble;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unchecked")
public class Asserts {

    public static Duration defaultTimeOut = Duration.ofSeconds(10);
    public static Duration defaultPollInterval = Duration.ofMillis(10);
    private static final Pattern FLAG = Pattern.compile("\\?([\\S]+)(?:[\\s\\n]([\\S\\s]*))?");

    // ↓ Equals ↓

    public static void equals(Object actual, Object expected) {
        equalsInAnyOrder(actual, expected);
    }

    public static void equalsInOrder(Object actual, Object expected) {
        equals(actual, expected, true);
    }

    public static void equalsInAnyOrder(Object actual, Object expected) {
        equals(actual, expected, false);
    }

    public static void equals(Object actual, Object expected, boolean inOrder) {
        List<String> errors = new ArrayList<>();
        equals(actual, expected, inOrder, Path.start(), errors);
        if (!errors.isEmpty()) {
            Assert.fail(String.join("\n", errors));
        }
    }

    private static void equals(Object actual, Object expected, boolean inOrder, Path path, Collection<String> errors) {
        if (nullBooleanAndNumberCheckIsOkay(actual, expected, path, errors)) {
            if (actual instanceof String && expected instanceof String) {
                try {
                    if (FLAG.matcher((String) expected).matches()) {
                        withTryCatch(() -> equals((String) actual, (String) expected), path, errors);
                    } else if (((String) actual).startsWith("{")) {
                        equals(Mapper.read((String) actual, Map.class), Mapper.read((String) expected, Map.class), inOrder, path, errors);
                    } else if (((String) actual).startsWith("[")) {
                        equals(Mapper.read((String) actual, List.class), Mapper.read((String) expected, List.class), inOrder, path, errors);
                    } else {
                        withTryCatch(() -> equals((String) actual, (String) expected), path, errors);
                    }
                } catch (Exception e) {
                    // our guess about the Lists and Maps were wrong, let's fallback to plain text
                    withTryCatch(() -> equals((String) actual, (String) expected), path, errors);
                }
            } else if (actual instanceof Map && expected instanceof Map) {
                equals((Map<String, Object>) actual, (Map<String, Object>) expected, inOrder, path, errors);
            } else if (expected instanceof Map) {
                equals(Mapper.read(Mapper.toYaml(actual), Map.class), (Map<String, Object>) expected, inOrder, path, errors);
            } else if (actual instanceof List) {
                if (expected instanceof List) {
                    equals((List<Object>) actual, (List<Object>) expected, inOrder, path, errors);
                } else {
                    if (Mapper.isList((String) expected)) {
                        equals((List<Object>) actual, Mapper.read((String) expected, List.class), inOrder, path, errors);
                    } else {
                        equals((List<Object>) actual, List.of(expected), inOrder, path, errors);
                    }
                }
            } else {
                equals(Mapper.toNonDefaultJson(actual), Mapper.toNonDefaultJson(expected), inOrder, path, errors);
            }
        }
    }

    private static void equals(String actual, String expected) {
        switch (getFlag(expected)) {
            case "e" -> assertThat(actual).matches(stripped(expected));
            case "contains" -> assertThat(actual).contains(stripped(expected));
            case "doesNotContain" -> assertThat(actual).doesNotContain(stripped(expected));
            case "eq", "==" -> assertThat(actual).isEqualTo(stripped(expected));
            case "w" -> assertThat(actual).isEqualToIgnoringWhitespace(stripped(expected));
            case "gt", ">" -> assertThat(parseDouble(actual)).isGreaterThan(parseDouble(stripped(expected)));
            case "ge", ">=" -> assertThat(parseDouble(actual)).isGreaterThanOrEqualTo(parseDouble(stripped(expected)));
            case "lt", "<" -> assertThat(parseDouble(actual)).isLessThan(parseDouble(stripped(expected)));
            case "le", "<=" -> assertThat(parseDouble(actual)).isLessThanOrEqualTo(parseDouble(stripped(expected)));
            case "not", "ne", "!=" -> assertThat(actual).isNotEqualTo(stripped(expected));
            case "in" -> assertThat(Mapper.read(stripped(expected), List.class)).contains(actual);
            case "notIn" -> assertThat(Mapper.read(stripped(expected), List.class)).doesNotContain(actual);
            case "isNull" -> assertThat(actual).isNull();
            case "notNull" -> assertThat(actual).isNotNull();
            case "base64" -> assertThat(new String(Base64.getEncoder().encode(actual.getBytes(UTF_8)), UTF_8)).isEqualTo(stripped(expected));
            case "isUUID" -> assertThat(actual).matches("\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b");
            case "before" -> assertThat(Instant.parse(actual)).isBefore(Instant.parse(stripped(expected))); // assuming Instant
            case "after" -> assertThat(Instant.parse(actual)).isAfter(Instant.parse(stripped(expected))); // assuming Instant
            case "is" -> Mapper.read(actual, TypeParser.parse(stripped(expected)));
            case "ignore" -> {} // ignore the value
            default -> assertEquals(expected, actual);
        }
    }

    private static String stripped(String expected) {
        Matcher matcher = FLAG.matcher(expected);
        return matcher.matches() ? matcher.group(2) : expected;
    }

    private static String getFlag(String expected) {
        Matcher matcher = FLAG.matcher(expected);
        return matcher.matches() ? matcher.group(1) : "no-flag";
    }

    private static void equals(Map<String, Object> actual, Map<String, Object> expected, boolean inOrder, Path path, Collection<String> errors) {
        withFailMessage(() -> assertThat(actual.size()).isEqualTo(expected.size()), () -> """
                %s
                doesn't have the same size than:
                %s
                """.formatted(Mapper.toYaml(actual), Mapper.toYaml(expected)));
        expected.forEach((key, expectedValue) -> equals(actual.get(key), expectedValue, inOrder, path.append("." + key), errors));
    }

    private static void equals(List<Object> actual, List<Object> expected, boolean inOrder, Path path, Collection<String> errors) {
        withFailMessage(() -> assertThat(actual.size()).isEqualTo(expected.size()), () -> """
                %s
                doesn't have the same size than:
                %s
                """.formatted(Mapper.toYaml(actual), Mapper.toYaml(expected)));
        List<String> listErrors = new ArrayList<>();
        if (inOrder) {
            for (int i = 0; i < expected.size(); i++) {
                equals(actual.get(i), expected.get(i), true, path.append("[" + i + "]"), listErrors);
            }
        } else {
            for (int i = 0; i < expected.size(); i++) {
                Set<String> elementErrors = new LinkedHashSet<>();
                Path element = path.append("[" + i + "]");
                boolean match = false;
                for (Object o : actual) {
                    int currentErrors = elementErrors.size();
                    equals(o, expected.get(i), false, element, elementErrors);
                    if (currentErrors == elementErrors.size()) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    listErrors.add(elementErrors.stream().map(e -> e.replaceAll("\\n", " ")).collect(Collectors.joining("\n\t")));
                }
            }
        }
        if (!listErrors.isEmpty()) {
            errors.add("""
                                        
                    %s
                    is not equal to expected:
                    \t%s
                    """.formatted(Mapper.toYaml(actual), String.join("\n\t", listErrors)));
        }
    }

    // ↓ Contains ↓

    public static void containsOnly(Object actual, Object expected) {
        contains(actual, expected, true, false);
    }

    public static void containsOnlyInOrder(Object actual, Object expected) {
        contains(actual, expected, true, true);
    }

    public static void contains(Object actual, Object expected) {
        contains(actual, expected, false, false);
    }

    public static void containsInOrder(Object actual, Object expected) {
        contains(actual, expected, false, true);
    }

    public static void contains(Object actual, Object expected, boolean strictListSize, boolean inOrder) {
        List<String> errors = new ArrayList<>();
        contains(actual, expected, strictListSize, inOrder, Path.start(), errors);
        if (!errors.isEmpty()) {
            Assert.fail(String.join("\n", errors));
        }
    }

    private static void contains(Object actual, Object expected, boolean strictListSize, boolean inOrder, Path path, Collection<String> errors) {
        if (nullBooleanAndNumberCheckIsOkay(actual, expected, path, errors)) {
            if (actual instanceof String && expected instanceof String) {
                try {
                    if (((String) actual).startsWith("{")) {
                        contains(Mapper.read((String) actual, Map.class), Mapper.read((String) expected, Map.class), strictListSize, inOrder, path, errors);
                    } else if (((String) actual).startsWith("[")) {
                        contains(Mapper.read((String) actual, List.class), Mapper.read((String) expected, List.class), strictListSize, inOrder, path, errors);
                    } else {
                        withTryCatch(() -> equals((String) actual, (String) expected), path, errors);
                    }
                } catch (Exception e) {
                    // our guess about the Lists and Maps were wrong, let's fallback to plain text
                    withTryCatch(() -> equals((String) actual, (String) expected), path, errors);
                }
            } else if (actual instanceof Map && expected instanceof Map) {
                contains((Map<String, Object>) actual, (Map<String, Object>) expected, strictListSize, inOrder, path, errors);
            } else if (expected instanceof Map) {
                contains(Mapper.read(Mapper.toYaml(actual), Map.class), (Map<String, Object>) expected, strictListSize, inOrder, path, errors);
            } else if (actual instanceof List) {
                if (expected instanceof List) {
                    contains((List<Object>) actual, (List<Object>) expected, strictListSize, inOrder, path, errors);
                } else {
                    if (Mapper.isList((String) expected)) {
                        contains((List<Object>) actual, Mapper.read((String) expected, List.class), strictListSize, inOrder, path, errors);
                    } else {
                        contains((List<Object>) actual, List.of(expected), strictListSize, inOrder, path, errors);
                    }
                }
            } else {
                contains(Mapper.toNonDefaultJson(actual), Mapper.toNonDefaultJson(expected), strictListSize, inOrder, path, errors);
            }
        }
    }

    private static void contains(Map<String, Object> actual, Map<String, Object> expected, boolean strictListSize, boolean inOrder, Path path, Collection<String> errors) {
        expected.forEach((key, expectedValue) -> contains(actual.get(key), expectedValue, strictListSize, inOrder, path.append("." + key), errors));
    }

    private static void contains(List<Object> actual, List<Object> expected, boolean strictListSize, boolean inOrder, Path path, Collection<String> errors) {
        if (strictListSize) {
            withTryCatch(() -> assertThat(actual).hasSameSizeAs(expected), path, errors);
        } else {
            withTryCatch(() -> assertThat(actual).size().isGreaterThanOrEqualTo(expected.size()), path, errors);
        }
        List<String> listErrors = new ArrayList<>();
        Set<Integer> matches = new LinkedHashSet<>();

        int j = 0;
        for (int i = 0; i < expected.size(); i++) {
            Set<String> elementErrors = new LinkedHashSet<>();
            boolean match = false;
            if (!inOrder) {
                j = 0; // we start again only if we don't expect the content to be ordered
            }
            while (j < actual.size()) {
                int currentErrors = elementErrors.size();
                Path element = path.append("[" + i + "]!=[" + j + "]");
                contains(actual.get(j), expected.get(i), strictListSize, inOrder, element, elementErrors);
                j++;
                if (currentErrors == elementErrors.size() && !matches.contains(j)) {
                    matches.add(j);
                    match = true;
                    break;
                }
            }
            if (!match) {
                listErrors.add(elementErrors.stream().map(e -> e.replaceAll("\\n", " ")).collect(Collectors.joining("\n\t")));
            }
        }

        if (!listErrors.isEmpty()) {
            errors.add("""
                                        
                    %s
                    doesn't contain expected:
                    \t%s
                    """.formatted(Mapper.toYaml(actual), String.join("\n\t", listErrors)));
        }
    }

    // ↓ Utils ↓

    public static void awaitUntilAsserted(ThrowingRunnable runnable) {
        awaitUntilAsserted(runnable, defaultTimeOut);
    }

    public static void awaitUntilAsserted(ThrowingRunnable runnable, Duration timeOut) {
        await().pollDelay(Duration.ZERO).pollInterval(defaultPollInterval).atMost(timeOut).untilAsserted(runnable);
    }

    public static void awaitDuring(ThrowingRunnable runnable, Duration timeOut) {
        await().pollDelay(Duration.ZERO).pollInterval(defaultPollInterval).during(timeOut).atMost(timeOut.plusMillis(100)).untilAsserted(runnable);
    }

    public static void awaitUntil(Callable<Boolean> callable) {
        awaitUntil(callable, defaultTimeOut);
    }

    public static void awaitUntil(Callable<Boolean> callable, Duration timeOut) {
        await().pollDelay(Duration.ZERO).pollInterval(defaultPollInterval).atMost(timeOut).until(callable);
    }

    public static <T extends Throwable> void threwException(org.junit.function.ThrowingRunnable runnable, Class<T> expectedException) {
        Assert.assertThrows(expectedException, runnable);
    }

    private static boolean nullBooleanAndNumberCheckIsOkay(Object actual, Object expected, Path path, Collection<String> errors) {
        if (actual != null || expected != null) {
            if (actual == null) {
                if (!"?isNull".equals(expected) && !"?ignore".equals(expected)) {
                    errors.add(path.failedWith("actual was null, was expecting: %s".formatted(expected)));
                }
            } else if (expected == null) {
                errors.add(path.failedWith("expecting null, but was: %s".formatted(actual)));
            } else if (actual instanceof Number && expected instanceof Number) {
                withTryCatch(() -> equals(String.valueOf(actual), String.valueOf(expected)), path, errors);
            } else if (actual instanceof Boolean && expected instanceof Boolean) {
                withTryCatch(() -> equals(String.valueOf(actual), String.valueOf(expected)), path, errors);
            } else {
                return true;
            }
        }
        return false;
    }

    private static void withTryCatch(Runnable runnable, Path path, Collection<String> errors) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            errors.add(path.failedWith(throwable.getMessage()));
        }
    }

    public static void withFailMessage(Runnable runnable, Supplier<String> withError) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            throw new AssertionError(withError.get());
        }
    }

    private static class Path {

        protected final String path;

        private Path(String path) {
            this.path = path;
        }

        private Path append(String next) {
            return new Path(path + next);
        }

        private String failedWith(String message) {
            return path + "' -> " + message;
        }

        private static Path start() {
            return new Path("");
        }

        @Override
        public String toString() {
            return path;
        }
    }
}
