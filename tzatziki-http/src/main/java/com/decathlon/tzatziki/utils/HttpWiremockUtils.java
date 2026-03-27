package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.steps.HttpSteps;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.MultiValue;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.decathlon.tzatziki.steps.HttpSteps.MOCKED_PATHS;
import static java.util.stream.Collectors.toMap;

@Slf4j
@SuppressWarnings({
        "java:S5960",// Address Sonar warning: False positive assertion check on non-production code.
        "java:S1118" // Utility class should not have constructor
})
public class HttpWiremockUtils {

    private static final String PROTOCOL = "(?:([^:]+)://)?";
    private static final String HOST = "([^/]+)?";
    private static final Pattern URI = Pattern.compile("^" + PROTOCOL + HOST + "((/[^?]*)?(?:\\?(.+))?)?$"); // NOSONAR

    public static String mocked(String path) {
        Matcher uri = match(path);
        if (uri.group(2) != null) {
            MOCKED_PATHS.add(uri.group(1) + "://" + uri.group(2));
            return remapAsMocked(uri);
        }
        return path;
    }

    public static void removeMocked(String path) {
        Matcher uri = match(path);
        if (uri.group(2) != null) {
            MOCKED_PATHS.remove(uri.group(1) + "://" + uri.group(2));
        }
    }

    public static Matcher match(String path) {
        Matcher uri = URI.matcher(path);
        if (!uri.matches()) {
            Assertions.fail("invalid uri: " + path);
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
        return "http://localhost:" + HttpSteps.localPort;
    }

    public static Map<String, String> asMap(Collection<HttpHeader> headers) {
        return headers.stream().collect(toMap(
                MultiValue::getKey,
                header -> header.getValues().get(0)));
    }


    static List<Pair<String, String>> parseQueryParams(String queryParams) {
        if (StringUtils.isNotBlank(queryParams)) {
            return Splitter.on('&').splitToList(queryParams).stream()
                    .map(param -> {
                        List<String> splitted = Splitter.on('=').splitToList(param);
                        return Pair.of(splitted.get(0), splitted.get(1));
                    })
                    .toList();
        }
        return new ArrayList<>();
    }

    /**
     * Remaps the request URL of a WireMock stub mapping so that it is served under the internal
     * {@code /_mocked/<protocol>/<host><original_path>} prefix used by tzatziki.
     *
     * <p>This method mutates the given {@code stubMapping} in place: the first non-null URL field
     * found among {@code url}, {@code urlPath}, {@code urlPattern}, {@code urlPathPattern} and
     * {@code urlPathTemplate} is rewritten, preserving its match semantics (exact, path-only,
     * regex, …). See <a href="https://wiremock.org/docs/request-matching/">WireMock — request matching</a>.
     *
     * <p>If none of those fields is set, no mutation is performed and a warning is logged.
     *
     * @param stubMapping the WireMock stub mapping whose request URL should be remapped; mutated in place
     * @param protocol    the scheme to prepend ({@code "http"} or {@code "https"})
     */
    public static void addToMockedUrlOfStubMapping(StubMapping stubMapping, final String protocol) {
        final RequestPattern requestPattern = stubMapping.getRequest();
        Stream.<Pair<String, Function<String, UrlPattern>>>of(
                Pair.of(requestPattern.getUrlPath(),         WireMock::urlPathEqualTo),
                Pair.of(requestPattern.getUrl(),             WireMock::urlEqualTo),
                Pair.of(requestPattern.getUrlPattern(),      WireMock::urlMatching),
                Pair.of(requestPattern.getUrlPathPattern(),  WireMock::urlPathMatching),
                Pair.of(requestPattern.getUrlPathTemplate(), WireMock::urlPathTemplate)
        )
        .filter(pair -> pair.getLeft() != null)
        .findFirst()
        .ifPresentOrElse(
                pair -> {
                    final String remappedMockUrl = HttpWiremockUtils.mocked(protocol + "://" + StringUtils.removeStart(pair.getLeft(), '/'));
                    stubMapping.setRequest(RequestPatternBuilder.like(requestPattern)
                            .withUrl(pair.getRight().apply(remappedMockUrl))
                            .build());
                },
                () -> log.warn("WireMock stub [id={}] has no recognizable URL field (url, urlPath, urlPattern, urlPathPattern, urlPathTemplate) — the request URL will not be remapped to /_mocked/<protocol>/<host>", stubMapping.getId())
        );
    }
}
