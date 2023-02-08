package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.matchers.StrictArrayContentJsonStringMatcher;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import com.sun.management.UnixOperatingSystemMXBean;
import io.netty.bootstrap.ServerBootstrap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.ConcurrentInitializer;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;
import org.mockserver.collections.CircularPriorityQueue;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.*;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.SortableExpectationId;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.model.*;
import org.mockserver.netty.MockServerUnificationInitializer;
import org.mockserver.verify.VerificationTimes;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.decathlon.tzatziki.utils.Fields.getValue;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MockFaster {

    public static final ExpectationResponseCallback NOT_FOUND = httpRequest -> HttpResponse.response()
            .withReasonPhrase("Not Found Again!")
            .withStatusCode(404);

    public static final String INVALID_REGEX_PATTERN = "(\\[])";
    private static final String PROTOCOL = "(?:([^:]+)://)?";
    private static final String HOST = "([^/]+)?";
    private static final Pattern URI = Pattern.compile("^" + PROTOCOL + HOST + "((/[^?]+)?(?:\\?(.+))?)?$");
    private static final ClientAndServer CLIENT_AND_SERVER = new ClientAndServer();
    private static final ConcurrentInitializer<Integer> LOCAL_PORT = new LazyInitializer<>() {
        @Override
        protected Integer initialize() {
            return CLIENT_AND_SERVER.getLocalPort();
        }
    };
    private static final Map<String, Pair<Expectation[], UpdatableExpectationResponseCallback>> MOCKS = new LinkedHashMap<>();
    private static final ConcurrentInitializer<HttpState> HTTP_STATE = new LazyInitializer<>() {

        @Override
        protected HttpState initialize() throws ConcurrentException {
            org.mockserver.netty.MockServer mockserver = getValue(CLIENT_AND_SERVER, "mockServer");
            ServerBootstrap serverServerBootstrap = getValue(mockserver, "serverServerBootstrap");
            MockServerUnificationInitializer childHandler = getValue(serverServerBootstrap, "childHandler");
            System.setProperty("mockserver.webSocketClientEventLoopThreadCount", "1");
            return getValue(childHandler, "httpState");
        }
    };
    private static final Set<String> MOCKED_PATHS = new LinkedHashSet<>();
    private static int latestPriority = 0;

    public static synchronized void add_mock(HttpRequest httpRequest, ExpectationResponseCallback callback, Comparison comparison) {
        HttpState httpState = unchecked(HTTP_STATE::get);
        CircularPriorityQueue<String, HttpRequestMatcher, SortableExpectationId> expectationsQueue = Fields.getValue(httpState.getRequestMatchers(), "httpRequestMatchers");

        AtomicBoolean isNew = new AtomicBoolean(false);
        latestPriority++;
        final Pair<Expectation[], UpdatableExpectationResponseCallback> expectationWithCallback = MOCKS.computeIfAbsent(httpRequest.toString(), k -> {
            isNew.set(true);
            UpdatableExpectationResponseCallback updatableCallback = new UpdatableExpectationResponseCallback();
            Expectation[] expectations = CLIENT_AND_SERVER.when(httpRequest, Times.unlimited(), TimeToLive.unlimited(), latestPriority).respond(updatableCallback);

            modifyJsonStrictnessMatcher(
                    Arrays.stream(expectations)
                            .map(Expectation::getId)
                            .map(expectationsQueue::getByKey)
                            .map(optionalRequestMatcher -> optionalRequestMatcher.orElseThrow(() ->
                                    new IllegalStateException("couldn't find the old expectation in the queue for strictness modification")))
                            .toList(),
                    comparison);

            return Pair.of(expectations, updatableCallback);
        });
        expectationWithCallback.getValue().set(callback);

        if (!isNew.get()) {
            Arrays.stream(expectationWithCallback.getKey())
                    // update the priority of the expectation
                    .map(expectation -> expectation.withPriority(latestPriority))
                    // re-add the expectations, this will resort the CircularPriorityQueue
                    .forEach(expectation -> {
                        httpState.getRequestMatchers().add(expectation, MockServerMatcherNotifier.Cause.API);

                        String clientId = expectation.getHttpResponseObjectCallback().getClientId();
                        Map<String, ExpectationResponseCallback> responseCallbackRegistry = LocalCallbackRegistry.responseCallbackRegistry();
                        responseCallbackRegistry.remove(clientId);
                        responseCallbackRegistry.put(clientId, expectationWithCallback.getValue());
                    });
        }

        if (httpRequest.getPath() instanceof NottableSchemaString uriSchema) {
            PATH_PATTERNS.add(Pattern.compile(((ObjectNode) getValue(uriSchema, "schemaJsonNode")).get("pattern").textValue()));
        } else {
            PATH_PATTERNS.add(Pattern.compile(httpRequest.getPath().getValue()));
        }
    }

    private static void modifyJsonStrictnessMatcher(List<HttpRequestMatcher> httpRequestMatchers, Comparison comparison) {
        httpRequestMatchers.forEach(requestMatcher -> {
            if (requestMatcher instanceof HttpRequestPropertiesMatcher httpRequestPropertiesMatcher) {
                final Object bodyMatcher = getValue(httpRequestPropertiesMatcher, "bodyMatcher");

                if (bodyMatcher instanceof JsonStringMatcher jsonStringMatcher) {
                    if (comparison == Comparison.CONTAINS_ONLY_IN_ORDER || comparison == Comparison.IS_EXACTLY) {
                        Fields.setValue(jsonStringMatcher, "matchType", MatchType.STRICT);
                    } else if (comparison == Comparison.CONTAINS_ONLY) {
                        Fields.setValue(httpRequestPropertiesMatcher,
                                "bodyMatcher", new StrictArrayContentJsonStringMatcher(jsonStringMatcher));
                    }
                }
            }
        });
    }

    public static Matcher match(String path) {
        Matcher uri = URI.matcher(path);
        if (!uri.matches()) {
            Assert.fail("invalid uri: " + path);
        }
        return uri;
    }

    public static String mocked(String path) {
        Matcher uri = match(path);
        if (uri.group(2) != null) {
            MOCKED_PATHS.add(uri.group(1) + "://" + uri.group(2));
            return remapAsMocked(uri);
        }
        return path;
    }

    public static String target(String path) {
        Matcher uri = match(path);
        if (uri.group(2) != null && MOCKED_PATHS.contains(uri.group(1) + "://" + uri.group(2))) {
            return url() + remapAsMocked(uri);
        }
        return path;
    }

    @NotNull
    private static String remapAsMocked(Matcher uri) {
        return "/_mocked/" + uri.group(1) + "/" + uri.group(2) + uri.group(3);
    }

    public static Integer localPort() {
        return unchecked(LOCAL_PORT::get);
    }

    private static final Set<Pattern> PATH_PATTERNS = new LinkedHashSet<>();

    public static List<LogEventRequestAndResponse> retrieveRequestResponses(HttpRequest httpRequest) {
        PATH_PATTERNS.stream()
                .map(pathPattern -> pathPattern.matcher(httpRequest.getPath().getValue()))
                .filter(Matcher::matches)
                .filter(matcher -> matcher.groupCount() > 0)
                .findFirst()
                .ifPresent(matcher -> {
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        httpRequest.withPathParameter("param" + i, matcher.group(i));
                    }
                });
        List<LogEventRequestAndResponse> requestResponses = new ArrayList<>();
        CompletableFuture<Void> waiter = new CompletableFuture<>();
        unchecked(HTTP_STATE::get).getMockServerLog().retrieveRequestResponses(httpRequest, logEventRequestAndResponses -> {
            requestResponses.addAll(logEventRequestAndResponses);
            waiter.complete(null);
        });
        waiter.join(); // the log retriever is async
        return requestResponses;
    }

    public static List<HttpRequest> retrieveRecordedRequests(HttpRequest httpRequest) {
        return retrieveRequestResponses(httpRequest).stream()
                .map(LogEventRequestAndResponse::getHttpRequest)
                .map(HttpRequest.class::cast)
                .collect(toList());
    }

    public static void verify(HttpRequest httpRequest) {
        verify(httpRequest, VerificationTimes.atLeast(1));
    }

    public static void verify(HttpRequest httpRequest, VerificationTimes times) {
        List<LogEventRequestAndResponse> requests = retrieveRequestResponses(httpRequest);
        if (!times.matches(requests.size())) {
            throw new AssertionError("""
                    Request was expected %s but was received %s, expected: %s but was: %s
                    """.formatted(times.toString(), VerificationTimes.exactly(requests.size()), httpRequest.toString(), requests));
        }
    }

    public static Map<String, String> asMap(List<Header> headers) {
        return headers.stream().collect(toMap(
                header -> header.getName().getValue(),
                header -> header.getValues().get(0).toString()));
    }

    public static void compareHeaders(Comparison comparison, HttpRequest request, List<Header> headers) {
        if (!headers.isEmpty()) {
            Map<String, String> actual = asMap(request.getHeaderList());
            comparison.compare(actual, asMap(headers));
        }
    }

    public static void compareQueryStringParameters(Comparison comparison, HttpRequest request, String queryParams) {
        comparison.compare(request.getQueryStringParameterList(), toParameters(queryParams));
    }

    public static void compareBodies(Comparison comparison, HttpRequest request, String body) {
        if (StringUtils.isNotBlank(body)) {
            comparison.compare(request.getBodyAsString(), body);
        }
    }

    public static List<Parameter> toParameters(String queryParams) {
        return toParameters(queryParams, true);
    }

    public static List<Parameter> toParameters(String queryParams, boolean evictCapturingGroups) {
        if (StringUtils.isNotBlank(queryParams)) {
            return Splitter.on('&').splitToList(queryParams).stream()
                    .map(param -> {
                        List<String> splitted = Splitter.on('=').splitToList(param);
                        return Pair.of(splitted.get(0), splitted.get(1));
                    })
                    .collect(groupingBy(Pair::getKey))
                    .entrySet().stream()
                    .filter(e -> !(evictCapturingGroups && e.getValue().get(0).getValue().matches("^(?:\\.\\*|\\(.*\\))$")))
                    .map(e -> Parameter.param(e.getKey(), e.getValue().stream().map(Pair::getValue).collect(toList())))
                    .collect(toList());
        }
        return new ArrayList<>();
    }

    public static String toQueryString(List<Parameter> parameters) {
        return parameters.stream()
                .flatMap(parameter -> parameter.getValues().stream().map(value -> parameter.getName() + "=" + value))
                .sorted()
                .collect(joining("&"));
    }

    public static void assertHasReceivedAtLeast(List<HttpRequest> expectedRequests) {
        assertHasReceived(Comparison.CONTAINS, expectedRequests);
    }

    public static void assertHasReceived(Comparison comparison, List<HttpRequest> expectedRequests) {
        List<HttpRequest> recordedRequests = expectedRequests.stream()
                .map(httpRequest -> retrieveRequestResponses(httpRequest.clone().withBody((Body<?>) null)))
                .flatMap(Collection::stream)
                .collect(toMap(e -> e.getHttpRequest().getLogCorrelationId(), identity(), (h1, h2) -> h1))
                .values()
                .stream()
                .sorted(Comparator.comparing(LogEventRequestAndResponse::getTimestamp))
                .map(log -> (HttpRequest) log.getHttpRequest())
                .toList();

        comparison.compare(
                recordedRequests.stream().map(HttpRequest::getBodyAsString).collect(toList()),
                expectedRequests.stream().map(HttpRequest::getBodyAsString).map(value -> value == null ? "?ignore" : value).collect(toList()));
    }

    public static void assertHasReceivedAtLeast(HttpRequest expectedRequest) {
        assertHasReceived(Comparison.CONTAINS, expectedRequest);
    }

    public static void assertHasReceived(Comparison comparison, HttpRequest expectedRequest) {
        List<HttpRequest> requests = retrieveRecordedRequests(expectedRequest.clone().withHeaders(Collections.emptyList()).withBody((Body<?>) null));
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        requests.stream().filter(recorded -> {
                    try {
                        compareHeaders(comparison, recorded, expectedRequest.getHeaderList());
                        compareBodies(comparison, recorded, expectedRequest.getBodyAsString());
                    } catch (Throwable e) {
                        throwable.set(e);
                        return false;
                    }
                    return true;
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("""
                        Request not found at least once, expected: %s but was: %s
                        """.formatted(expectedRequest.toString(), requests), throwable.get()));
    }

    public static String url() {
        return "http://localhost:" + localPort();
    }

    public static ClientAndServer clientAndServer() {
        return CLIENT_AND_SERVER;
    }

    public static void reset() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof UnixOperatingSystemMXBean unixOs && (unixOs.getMaxFileDescriptorCount() - unixOs.getOpenFileDescriptorCount() < Long.parseLong(System.getProperty("mockfaster.fd.threshold", "300")))) {
            System.err.println("resetting mockserver instance not to exceed the max amount of mocks");
            CLIENT_AND_SERVER.reset();
            MOCKS.clear();
        } else {
            MOCKS.values().forEach(expectationIdsWithUpdatableCallback -> expectationIdsWithUpdatableCallback.getValue().set(NOT_FOUND));
            unchecked(HTTP_STATE::get).getMockServerLog().reset();
        }
        MOCKED_PATHS.clear();
        PATH_PATTERNS.clear();
    }

    public static void stop() {
        reset();
        CLIENT_AND_SERVER.stop();
    }

    private static class UpdatableExpectationResponseCallback implements ExpectationResponseCallback {

        private ExpectationResponseCallback callback;

        @Override
        public synchronized HttpResponse handle(HttpRequest httpRequest) throws Exception {
            return callback.handle(httpRequest);
        }

        public void set(ExpectationResponseCallback callback) {
            this.callback = callback;
        }
    }

    public static MockBuilder when(HttpRequest request, Comparison comparison) {
        return new MockBuilder(request, comparison);
    }

    public static class MockBuilder {
        private final HttpRequest request;
        private final Comparison comparison;

        public MockBuilder(HttpRequest request, Comparison comparison) {
            this.request = request;
            this.comparison = comparison;
        }

        public void respond(HttpResponse response) {
            respond(request -> response);
        }

        public void respond(ExpectationResponseCallback callback) {
            add_mock(request, callback, comparison);
        }
    }

    public static String escapeBrackets(String string) {
        Matcher matcher = Pattern.compile(INVALID_REGEX_PATTERN).matcher(string);
        return matcher.replaceAll("\\\\[\\\\]");
    }
}
