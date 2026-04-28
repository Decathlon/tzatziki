package com.decathlon.tzatziki.utils;

import java.util.stream.Stream;

public enum InsertionMode {

    AT_LEAST("at least"), ONLY("only");

    public static final String INSERTION_MODE = "(?: (at least|only))?";

    private final String title;

    InsertionMode(String title) {
        this.title = title;
    }

    public static InsertionMode parse(String value) {
        return Stream.of(values())
                .filter(insertionMode -> insertionMode.title.equals(value))
                .findFirst()
                .orElse(AT_LEAST);
    }
}
