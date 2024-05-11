package com.decathlon.tzatziki.front.interactions;

import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
@Builder(toBuilder = true)
public class HTMLElementAssertion {
    public static final String STEPS_PATTERN = "((?: with attributes \\((?:\"[^:]+:[^:]+\"(?:, \"[^:]+:[^:]+\")*)\\))?(?: and)?(?: with style \\((?:\"[^:]+:[^:]+\"(?:, \"[^:]+:[^:]+\")*)\\))?)?";
    public static final String EXTRACT_PATTERN = "(?: with attributes \\((\"[^:]+:[^:]+\"(?:, \"[^:]+:[^:]+\")*)\\))?(?: and)?(?: with style \\((\"[^:]+:[^:]+\"(?:, \"[^:]+:[^:]+\")*)\\))?";

    private Map<String, String> attributes;
    private Map<String, String> styles;

    public static HTMLElementAssertion parse(String value) {
        if (StringUtils.isNotBlank(value)) {
            final Matcher matcher = Pattern.compile(EXTRACT_PATTERN).matcher(value);

            if (!matcher.find()) {
                return always();
            }
            return extractHTMLElementAssertion(matcher);
        } else {
            return always();
        }
    }

    private static HTMLElementAssertion extractHTMLElementAssertion(Matcher matcher) {
        Map<String, String> attributes = getPropertiesFromMatcherGroup(matcher, 1);
        Map<String, String> styles = getPropertiesFromMatcherGroup(matcher, 2);

        return HTMLElementAssertion.builder()
                .attributes(attributes)
                .styles(styles)
                .build();
    }

    private static @Nullable Map<String, String> getPropertiesFromMatcherGroup(Matcher matcher, int groupNumber) {
        return Optional.ofNullable(matcher.group(groupNumber)).map(group -> Arrays.stream(group.split(",")).map(String::trim)
                .collect(Collectors.toMap(propertyString -> propertyString.split(":")[0].replaceAll("^\"|\"$", ""), e -> e.split(":")[1].replaceAll("^\"|\"$", "")))
        ).orElse(null);
    }

    private static HTMLElementAssertion always() {
        return HTMLElementAssertion.builder().build();
    }
}
