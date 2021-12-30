package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Patterns {

    public static final String A = "(?:(?:a|an|this|that|the|those|these) )";
    public static final String QUOTED_CONTENT = "\"([^\"]+)\"";
    public static final String THAT = "^(?:(?:that|when|then|if) )?";
    public static final String A_USER = "[\\w ]+ ";
    public static final String A_DURATION = "([\\d]+)ms";
    public static final String VARIABLE_PATTERN = "[_a-zA-Z][_\\-.\\w\\[\\]]*";
    public static final String VARIABLE = "(" + VARIABLE_PATTERN + ")";
    public static final String TYPE_PATTERN = "(?:[a-z_$][a-z0-9_$]*\\.)*[A-Z_$][A-z0-9_$]*(?:<.*>)?";
    public static final String TYPE = "(" + TYPE_PATTERN + ")";
    public static final String TYPE_OR_PACKAGE = "((?:[a-z_$][a-z0-9_$\\.]*)*(?:[A-z_$][A-z0-9_$]*))";
    public static final String NUMBER = "([0-9]+(?:\\.[0-9]+)?)";

}
