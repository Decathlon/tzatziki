package com.decathlon.tzatziki.front.interactions;

import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.decathlon.tzatziki.utils.Patterns.A_DURATION;
import static com.decathlon.tzatziki.utils.Patterns.QUOTED_CONTENT;

@Getter
@Builder(toBuilder = true)
public class BrowserAssertion {
    public static final String STEPS_PATTERN = "( (?:waiting|contains) \"(?:[^\"]+)\"(?: visible)?" + "(?: within (?:[\\d]+)ms)?" + ")?";
    public static final String EXTRACT_PATTERN = "(?: (?:waiting|contains) " + QUOTED_CONTENT + "(?: (visible))?" + "(?: within " + A_DURATION + ")?" + ")?";

    private int timeoutMs = 30_000;
    private String selector;
    private boolean isVisible = false;

    public static BrowserAssertion parse(String value) {
        if (value != null) {
            final Matcher matcher = Pattern.compile(EXTRACT_PATTERN).matcher(value);

            if (!matcher.find()) {
                return always();
            }
            return extractBrowserAssertion(matcher);
        } else {
            return always();
        }
    }

    private static BrowserAssertion extractBrowserAssertion(Matcher matcher) {
        return BrowserAssertion.builder().selector(matcher.group(1))
                .isVisible(StringUtils.isNotBlank(matcher.group(2)))
                .timeoutMs(Integer.parseInt(matcher.group(3)))
                .build();
    }

    private static BrowserAssertion always() {
        return BrowserAssertion.builder().build();
    }
}
