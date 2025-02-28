package com.decathlon.tzatziki.utils;

import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.MultiValue;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toMap;

public class HttpUtils {

    private static final String PROTOCOL = "(?:([^:]+)://)?";
    private static final String HOST = "([^/]+)?";
    private static final Pattern URI = Pattern.compile("^" + PROTOCOL + HOST + "((/[^?]*)?(?:\\?(.+))?)?$");
    public static Integer localPort;

    public static String mocked(String path) {
        Matcher uri = match(path);
        if (uri.group(2) != null) {
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
        if (uri.group(2) != null) {
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
}
