package com.decathlon.tzatziki.utils;

import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.MultiValue;
import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.decathlon.tzatziki.steps.HttpSteps.MOCKED_PATHS;
import static java.util.stream.Collectors.toMap;

public class HttpUtils {

    private static final String PROTOCOL = "(?:([^:]+)://)?";
    private static final String HOST = "([^/]+)?";
    private static final Pattern URI = Pattern.compile("^" + PROTOCOL + HOST + "((/[^?]*)?(?:\\?(.+))?)?$");
    public static Integer localPort;

    public static String mocked(String path) {
        Matcher uri = match(path);
        if (uri.group(2) != null) {
            MOCKED_PATHS.add(uri.group(1) + "://" + uri.group(2));
            return remapAsMocked(uri);
        }
        return path;
    }

    public static Matcher match(String path) {
        Matcher uri = URI.matcher(path);
        if (!uri.matches()) {
            Assert.fail("invalid uri: " + path);
        }
        return uri;
    }

    @NotNull
    private static String remapAsMocked(Matcher uri) {
        return "/_mocked/" + uri.group(1) + "/" + uri.group(2) + uri.group(3);
    }

    public static String target(String path) {
        Matcher uri = match(path);
        if (uri.group(2) != null && MOCKED_PATHS.contains(uri.group(1) + "://" + uri.group(2))) {
            return url() + remapAsMocked(uri);
        }
        return path;
    }

    public static String url() {
        return "http://localhost:" + localPort;
    }

    public static Map<String, String> asMap(Collection<HttpHeader> headers) {
        return headers.stream().collect(toMap(
                MultiValue::getKey,
                header -> header.getValues().get(0)));
    }

    public static List<Pair<String, String>> parseQueryParams(String queryParams, boolean evictCapturingGroups) {
        if (StringUtils.isNotBlank(queryParams)) {
            return Splitter.on('&').splitToList(queryParams).stream()
                    .map(param -> {
                        List<String> splitted = Splitter.on('=').splitToList(param);
                        return Pair.of(splitted.get(0), splitted.get(1));
                    })
                    .filter(e -> !(evictCapturingGroups && e.getValue().matches("^(?:\\.\\*|\\(.*\\))$")))
                    .toList();
        }
        return new ArrayList<>();
    }
}
