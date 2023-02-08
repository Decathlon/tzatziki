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
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Double.parseDouble;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import java.math.BigDecimal;
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unchecked")
public class Asserts {
    public static Duration defaultTimeOut = Duration.ofSeconds(10);
    public static Duration defaultPollInterval = Duration.ofMillis(10);
    private static final Pattern FLAG = Pattern.compile("\\?([\\S]+)(?:[\\s\\n]([\\S\\s]*))?");
    private static final Map<String, BiConsumer<String, String>> CONSUMER_BY_FLAG = Collections.synchronizedMap(new LinkedHashMap<>());

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
            if (actual instanceof String actualString && expected instanceof String expectedString) {
                try {
                    if (FLAG.matcher(expectedString).matches()) {
                        withTryCatch(() -> equals(actualString, expectedString), path, errors);
                    } else if (actualString.startsWith("{")) {
                        equals(Mapper.read(actualString, Map.class), Mapper.read(expectedString, Map.class), inOrder, path, errors);
                    } else if (actualString.startsWith("[")) {
                        equals(Mapper.read(actualString, List.class), Mapper.read(expectedString, List.class), inOrder, path, errors);
                    } else {
                        withTryCatch(() -> equals(actualString, expectedString), path, errors);
                    }
                } catch (Exception e) {
                    // our guess about the Lists and Maps were wrong, lets fallback to plain text
                    withTryCatch(() -> equals(actualString, expectedString), path, errors);
                }
            } else if (actual instanceof Map actualMap && expected instanceof Map expectedMap) {
                equals(actualMap, expectedMap, inOrder, path, errors);
            } else if (expected instanceof Map expectedMap) {
                equals(Mapper.read(Mapper.toYaml(actual), Map.class), expectedMap, inOrder, path, errors);
            } else if (actual instanceof List actualList) {
                if (expected instanceof List expectedList) {
                    equals(actualList, expectedList, inOrder, path, errors);
                } else {
                    if (Mapper.isList((String) expected)) {
                        equals(actualList, Mapper.read((String) expected, List.class), inOrder, path, errors);
                    } else {
                        equals(actualList, List.of(expected), inOrder, path, errors);
                    }
                }
            } else {
                equals(Mapper.toJson(actual), Mapper.toJson(expected), inOrder, path, errors);
            }
        }
    }

    private static void equals(String actual, String expected) {
        Matcher matcher = FLAG.matcher(expected);
        if (matcher.matches()) {
            getConsumer(matcher.group(1)).accept(actual, matcher.group(2));
        } else {
            Matcher instantMatcher = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d*)?(\\+\\d+:\\d+|Z)?$").matcher(expected);
            if (instantMatcher.matches()) {
                if (instantMatcher.group(1) == null) {
                    expected += "Z";
                    actual += "Z";
                }
                assertEquals(Instant.parse(expected), Instant.parse(actual));
            } else {
                assertEquals(expected, actual);
            }
        }
    }

    private static BiConsumer<String, String> getConsumer(String flag) {
        return CONSUMER_BY_FLAG.computeIfAbsent(flag, value -> switch (value) {
            case "e" -> (actual, expected) -> assertThat(actual).matches(expected);
            case "contains" -> (actual, expected) -> assertThat(actual).contains(expected);
            case "doesNotContain" -> (actual, expected) -> assertThat(actual).doesNotContain(expected);
            case "eq", "==" -> (actual, expected) -> assertThat(actual).isEqualTo(expected);
            case "w" -> (actual, expected) -> assertThat(actual).isEqualToIgnoringWhitespace(expected);
            case "gt", ">" -> (actual, expected) -> assertThat(parseDouble(actual)).isGreaterThan(parseDouble(expected));
            case "ge", ">=" -> (actual, expected) -> assertThat(parseDouble(actual)).isGreaterThanOrEqualTo(parseDouble(expected));
            case "lt", "<" -> (actual, expected) -> assertThat(parseDouble(actual)).isLessThan(parseDouble(expected));
            case "le", "<=" -> (actual, expected) -> assertThat(parseDouble(actual)).isLessThanOrEqualTo(parseDouble(expected));
            case "not", "ne", "!=" -> (actual, expected) -> assertThat(actual).isNotEqualTo(expected);
            case "in" -> (actual, expected) -> assertThat(Mapper.read(expected, List.class)).contains(actual);
            case "notIn" -> (actual, expected) -> assertThat(Mapper.read(expected, List.class)).doesNotContain(actual);
            case "isNull" -> (actual, expected) -> assertThat(actual).isNull();
            case "notNull" -> (actual, expected) -> assertThat(actual).isNotNull();
            case "base64" -> (actual, expected) -> assertThat(new String(Base64.getEncoder().encode(actual.getBytes(UTF_8)), UTF_8)).isEqualTo(expected);
            case "isUUID" -> (actual, expected) -> assertThat(actual).matches("\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b");
            case "before" -> (actual, expected) -> assertThat(Instant.parse(actual)).isBefore(Instant.parse(expected)); // assuming Instant
            case "after" -> (actual, expected) -> assertThat(Instant.parse(actual)).isAfter(Instant.parse(expected)); // assuming Instant
            case "is" -> (actual, expected) -> Mapper.read(actual, TypeParser.parse(expected));
            case "hasDecimalValue" -> (actual, expected) -> assertThat(new BigDecimal(actual)).isLessThanOrEqualTo(new BigDecimal(actual));
            case "ignore" -> (actual, expected) -> {}; // ignore the value
            default -> (actual, expected) -> Assert.fail("invalid flag: " + flag);
        });
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
            if (actual instanceof String actualString && expected instanceof String expectedString) {
                try {
                    if (actualString.startsWith("{")) {
                        contains(Mapper.read(actualString, Map.class), Mapper.read(expectedString, Map.class), strictListSize, inOrder, path, errors);
                    } else if (actualString.startsWith("[")) {
                        contains(Mapper.read(actualString, List.class), Mapper.readAsAListOf(expectedString, Object.class), strictListSize, inOrder, path, errors);
                    } else {
                        withTryCatch(() -> equals(actualString, expectedString), path, errors);
                    }
                } catch (Exception e) {
                    // our guess about the Lists and Maps were wrong, lets fallback to plain text
                    withTryCatch(() -> equals(actualString, expectedString), path, errors);
                }
            } else if (expected instanceof String) {
                contains(Mapper.toJson(actual), expected, strictListSize, inOrder, path, errors);
            } else if (actual instanceof Map actualMap && expected instanceof Map expectedMap) {
                Map actualMapWithExpectedFieldsOnly = ((Map<Object, Object>) expectedMap).entrySet().stream().collect(
                        HashMap::new,
                        (map, entryToAdd) -> map.put(entryToAdd.getKey(), actualMap.get(entryToAdd.getKey())),
                        Map::putAll
                );
                contains(actualMapWithExpectedFieldsOnly, expectedMap, strictListSize, inOrder, path, errors);
            } else if (expected instanceof Map expectedMap) {
                contains("".equals(actual) ? Collections.emptyMap() : Mapper.read(Mapper.toYaml(actual), Map.class), expectedMap, strictListSize, inOrder, path, errors);
            } else if (actual instanceof List actualList) {
                if (expected instanceof List expecteList) {
                    contains(actualList, expecteList, strictListSize, inOrder, path, errors);
                } else {
                    if (Mapper.isList((String) expected)) {
                        contains(actualList, Mapper.read((String) expected, List.class), strictListSize, inOrder, path, errors);
                    } else {
                        contains(actualList, List.of(expected), strictListSize, inOrder, path, errors);
                    }
                }
            } else {
                contains(Mapper.toJson(actual), Mapper.toJson(expected), strictListSize, inOrder, path, errors);
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
        await().pollDelay(Duration.ZERO).pollInterval(defaultPollInterval).during(timeOut).atMost(timeOut.plusMillis(500)).untilAsserted(runnable);
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
                withTryCatch(() -> equals(actual.toString(), expected.toString()), path, errors);
            } else if (actual instanceof Boolean && expected instanceof Boolean) {
                withTryCatch(() -> equals(actual.toString(), expected.toString()), path, errors);
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

    /**
     * Can be used to provide additional flags to be interpreted along with the consumer to execute for the flag.
     * The consumer is a {@link BiConsumer} which takes ({@code actual}, {@code flagArgs[]})<br/><br/>
     * Usage:
     * <pre>
     * Asserts.addFlag("isEvenAndInBounds", (input, expected) -&gt; {
     *   String[] bounds = expected.split("\\|")
     *   int inputInt = Integer.parseInt(input);
     *   int min = Integer.parseInt(bounds[0].trim());
     *   int max = Integer.parseInt(bounds[1].trim());
     *   org.junit.jupiter.api.Assertions.assertTrue(() -&gt; inputInt &gt;= min &amp;&amp; inputInt &lt;= max &amp;&amp; inputInt % 2 == 0);
     * })
     *
     * Asserts.equals("2", "?isEvenAndInBounds 2 | 4");
     * </pre>
     *
     * @param flagName  the name to use in assertion to invoke the created flag. It should not contain the '?' ahead but should be used with it in assertions
     * @param assertion the consumer to invoke in case the flag is invoked.
     *                  The consumer takes ({@code actual}, {@code expected})
     */
    public static void addFlag(String flagName, BiConsumer<String, String> assertion) {
        CONSUMER_BY_FLAG.put(flagName, assertion);
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
