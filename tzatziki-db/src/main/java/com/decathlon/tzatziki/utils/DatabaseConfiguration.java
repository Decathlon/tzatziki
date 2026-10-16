package com.decathlon.tzatziki.utils;

import java.util.List;

/**
 * Shared fields inherited by database and Spring JPA steps, so both public configuration APIs remain aliases.
 */
public abstract class DatabaseConfiguration {

    public static boolean autoclean = true;
    public static List<String> schemasToClean = List.of("public");
}
