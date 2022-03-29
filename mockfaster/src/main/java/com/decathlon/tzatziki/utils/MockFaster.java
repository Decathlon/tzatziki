package com.decathlon.tzatziki.utils;

import com.google.common.base.Splitter;
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
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.model.*;
import org.mockserver.netty.MockServerUnificationInitializer;
import org.mockserver.verify.VerificationTimes;

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

    private static final String PROTOCOL = "(?:([^:]+)://)?";
    private static final String HOST = "([^/]+)?";
    private static final Pattern URI = Pattern.compile("^" + PROTOCOL + HOST + "((/[^?]+)?(?:\\?(.+))?)?$");
    private static final ClientAndServer CLIENT_AND_SERVER = new ClientAndServer();
    private static final ConcurrentInitializer<Integer> LOCAL_PORT = new LazyInitializer<Integer>() {
        @Override
        protected Integer initialize() throws ConcurrentException {
            return CLIENT_AND_SERVER.getLocalPort();
        }
    };
    private static final Map<String, Pair<List<String>, UpdatableExpectationResponseCallback>> MOCKS = new LinkedHashMap<>();
    private static final ConcurrentInitializer<HttpState> HTTP_STATE = new LazyInitializer<HttpState>() {

        @Override
        protected HttpState initialize() throws ConcurrentException {
            org.mockserver.netty.MockServer mockserver = getValue(CLIENT_AND_SERVER, "mockServer");
            ServerBootstrap serverServerBootstrap = getValue(mockserver, "serverServerBootstrap");
            MockServerUnificationInitializer childHandler = getValue(serverServerBootstrap, "childHandler");
            return getValue(childHandler, "httpStateHandler");
        }
    };
    private static final Set<String> MOCKED_PATHS = new LinkedHashSet<>();
    private static int latestPriority = 0;

    public static synchronized void add_mock(HttpRequest httpRequest, ExpectationResponseCallback callback) {
        HttpState httpState = unchecked(HTTP_STATE::get);
        httpState.getRequestMatchers().retrieveActiveExpectations(httpRequest)
                // we are redefining an old mock with a matches, let's see if we have old callbacks still in 404
                .stream()
                .filter(expectation -> MOCKS.containsKey(expectation.getHttpRequest().toString()))
                .filter(expectation -> MOCKS.get(expectation.getHttpRequest().toString()).getValue().callback.equals(NOT_FOUND))
                .forEach(expectation -> {
                    httpState.getRequestMatchers().clear(expectation.getHttpRequest());
                    MOCKS.remove(expectation.getHttpRequest().toString());
                    log.debug("removing expectation {}", expectation.getHttpRequest());
                });

        AtomicBoolean isNew = new AtomicBoolean(false);
        latestPriority++;
        final Pair<List<String>, UpdatableExpectationResponseCallback> expectationIdsWithCallback = MOCKS.computeIfAbsent(httpRequest.toString(), k -> {
            isNew.set(true);
            UpdatableExpectationResponseCallback updatableCallback = new UpdatableExpectationResponseCallback();
            final Expectation[] expectations = CLIENT_AND_SERVER.when(httpRequest, Times.unlimited(), TimeToLive.unlimited(), latestPriority).respond(updatableCallback);
            return Pair.of(Arrays.stream(expectations).map(Expectation::getId).toList(), updatableCallback);
        });
        expectationIdsWithCallback.getValue().set(callback);

        if (!isNew.get()) {
            final ExpectationId[] expectationIds = expectationIdsWithCallback.getKey().stream()
                    .map(expectationId -> new ExpectationId().withId(expectationId))
                    .toArray(ExpectationId[]::new);
            // update the priority of the expectation
            List<Expectation> expectations = httpState.getRequestMatchers()
                    .retrieveExpectations(expectationIds)
                    .map(expectation -> expectation.withPriority(latestPriority))
                    .toList();
            // re-add the expectations, this will resort the CircularPriorityQueue
            expectations.forEach(expectation -> httpState.getRequestMatchers().add(expectation, MockServerMatcherNotifier.Cause.API));
        }

        PATH_PATTERNS.add(Pattern.compile(httpRequest.getPath().getValue()));
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

    public static void assertHasHeaders(HttpRequest request, Map<String, String> headers) {
        compareHeaders(Comparison.CONTAINS, request, headers);
    }

    public static void compareHeaders(Comparison comparison, HttpRequest request, Map<String, String> headers) {
        if (!headers.isEmpty()) {
            Map<String, String> actual = request.getHeaderList().stream().collect(toMap(
                    header -> header.getName().getValue(),
                    header -> header.getValues().get(0).getValue()));
            comparison.compare(actual, headers);
        }
    }

    public static void assertHasQueryStringParameters(HttpRequest request, String queryParams) {
        compareQueryStringParameters(Comparison.CONTAINS, request, queryParams);
    }

    public static void compareQueryStringParameters(Comparison comparison, HttpRequest request, String queryParams) {
        comparison.compare(request.getQueryStringParameterList(), toParameters(queryParams));
    }

    public static void assertHasBody(HttpRequest request, String body) {
        compareBodies(Comparison.CONTAINS, request, body);
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
                expectedRequests.stream().map(HttpRequest::getBodyAsString).map(value -> value != null ? value : "?ignore").collect(toList()));
    }

    public static void assertHasReceivedAtLeast(HttpRequest expectedRequest) {
        assertHasReceived(Comparison.CONTAINS, expectedRequest);
    }

    public static void assertHasReceived(Comparison comparison, HttpRequest expectedRequest) {
        List<HttpRequest> requests = retrieveRecordedRequests(expectedRequest.clone().withBody((Body<?>) null));
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        requests.stream().filter(recorded -> {
                    try {
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
        MOCKS.values().forEach(expectationIdsWithUpdatableCallback -> expectationIdsWithUpdatableCallback.getValue().set(NOT_FOUND));
        unchecked(HTTP_STATE::get).getMockServerLog().reset();
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
        public HttpResponse handle(HttpRequest httpRequest) throws Exception {
            return callback.handle(httpRequest);
        }

        public void set(ExpectationResponseCallback callback) {
            this.callback = callback;
        }
    }

    public static MockBuilder when(HttpRequest request) {
        return new MockBuilder(request);
    }

    public static class MockBuilder {

        private final HttpRequest request;

        public MockBuilder(HttpRequest request) {
            this.request = request;
        }

        public void respond(HttpResponse response) {
            respond(request -> response);
        }

        public void respond(ExpectationResponseCallback callback) {
            add_mock(request, callback);
        }
    }
}
