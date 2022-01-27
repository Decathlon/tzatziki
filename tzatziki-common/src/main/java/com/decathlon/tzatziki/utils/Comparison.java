package com.decathlon.tzatziki.utils;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public enum Comparison {

    EQUALS(Asserts::equalsInAnyOrder, "is equal to", "=="),
    IS_EXACTLY(Asserts::equalsInOrder, "is exactly", "exactly"),
    CONTAINS(Asserts::contains, "contains?", "contains? at least", "at least"),
    CONTAINS_IN_ORDER(Asserts::containsInOrder, "contains? in order", "contains? at least and in order", "at least and in order", "in order"),
    CONTAINS_ONLY(Asserts::containsOnly, "contains? only", "only"),
    CONTAINS_ONLY_IN_ORDER(Asserts::containsOnlyInOrder, "contains? only and in order", "only and in order");

    public static final String IS_COMPARED_TO = "(?: (contains?|contains? only(?: and in order)?|contains? at least(?: and in order)?|contains? in order|contains? exactly|==|is equal to|is exactly))";
    public static final String COMPARING_WITH = "(?: (only(?: and in order)?|at least(?: and in order)?|in order|exactly))?";

    private final Set<String> titles;
    private final BiConsumer<Object, Object> function;

    Comparison(BiConsumer<Object, Object> function, String... titles) {
        this.titles = Set.of(titles);
        this.function = function;
    }

    public void compare(Object actual, Object expected) {
        function.accept(actual, expected);
    }

    public static Comparison parse(String value) {
        return Stream.of(values())
                .filter(comparison -> value != null && comparison.titles.stream().anyMatch(value::matches))
                .findFirst()
                .orElse(CONTAINS);
    }
}
