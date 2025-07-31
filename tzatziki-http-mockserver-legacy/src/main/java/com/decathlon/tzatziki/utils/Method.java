package com.decathlon.tzatziki.utils;

import static java.util.Locale.ROOT;

public enum Method {

    GET, POST, PUT, PATCH, DELETE, HEAD, TRACE, OPTIONS;

    public static final String CALL = "(?i)(call|get|head|post|put|patch|delete|trace)(?-i)(?:s|es)?";
    public static final String CALLING = "(calling|getting|heading|posting|putting|patching|deleting|tracing)";
    public static final String SEND = "(?i)(call|get|post|put|patch|delete)(?-i)(?:s|es)?";

    /**
     * Method from string value, stripping extra -ing suffixes and case-insensitive
     */
    public static Method of(String value) {
        value = value.toUpperCase(ROOT);
        if (value.endsWith("ING")) {
            value = value.substring(0, value.length() - "ING".length());
            if (value.endsWith("TT")) {
                value = value.substring(0, value.length() - 1);
            }
            if (value.equals("DELET")) {
                return DELETE;
            }
        }

        if (value.equals("CALL")) {
            return GET;
        }

        for (Method method : values()) {
            if (method.name().equals(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("unsupported method '" + value + "'");
    }
}
