package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import com.decathlon.tzatziki.utils.Interaction.Request;
import com.decathlon.tzatziki.utils.Interaction.Response;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationTimes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static com.decathlon.tzatziki.utils.Asserts.withFailMessage;
import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Guard.always;
import static com.decathlon.tzatziki.utils.Mapper.read;
import static com.decathlon.tzatziki.utils.Mapper.readAsAListOf;
import static com.decathlon.tzatziki.utils.Method.*;
import static com.decathlon.tzatziki.utils.MockFaster.*;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static io.restassured.RestAssured.given;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

@Slf4j
public class HttpSteps {

    public static final String STATUS = "([A-Z_]+[A-Z]|\\d+|[A-Z_]+_\\d+)";

    static {
        DynamicTransformers.register(Method.class, Method::of);
        DynamicTransformers.register(HttpStatusCode.class, HttpSteps::getHttpStatusCode);
    }

    private final Map<String, List<Pair<String, String>>> headersByUsername = new LinkedHashMap<>();
    private UnaryOperator<String> relativeUrlRewriter = UnaryOperator.identity();
    private boolean doNotAllowUnhandledRequests = true;
    private final Set<HttpRequest> allowedUnhandledRequests = new HashSet<>();

    private final ObjectSteps objects;

    public HttpSteps(ObjectSteps objects) {
        this.objects = objects;
        MockFaster.reset();
    }

    @Before(order = -1) // just for this instance to be created
    public void before() {
    }

    public void setRelativeUrlRewriter(UnaryOperator<String> relativeUrlRewriter) {
        this.relativeUrlRewriter = relativeUrlRewriter;
    }

    @Given(THAT + GUARD + "we allow unhandled mocked requests$")
    public void we_allow_unhandled_mocked_requests(Guard guard) {
        guard.in(objects, () -> this.doNotAllowUnhandledRequests = false);
    }


    @Given(THAT + GUARD + "we allow unhandled mocked requests " + CALLING + " (?:on )?" + QUOTED_CONTENT + "$")
    public void we_allow_unhandled_mocked_requests(Guard guard, Method method, String path) {
        guard.in(objects, () -> {
            String mocked = mocked(objects.resolve(path));
            Matcher uri = match(mocked);
            allowedUnhandledRequests.add(Request.builder().method(method).build().toHttpRequestIn(objects, uri, true));
        });
    }

    @Given(THAT + GUARD + "we allow unhandled mocked requests (?:on )?" + QUOTED_CONTENT + ":$")
    public void we_allow_unhandled_mocked_requests(Guard guard, String path, String content) {
        guard.in(objects, () -> {
            String mocked = mocked(objects.resolve(path));
            Matcher uri = match(mocked);
            allowedUnhandledRequests.add(read(objects.resolve(content), Request.class).toHttpRequestIn(objects, uri, true));
        });
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void calling_on_will_return_(Guard guard, Method method, String path, long delay, Type type, String content) {
        calling_on_will_return(guard, method, path, delay, type, content);
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return(?: " + A + TYPE + ")?:$")
    public void calling_on_will_return(Guard guard, Method method, String path, long delay, Type type, String content) {
        calling_on_will_return_a_status_and(guard, method, path, delay, HttpStatusCode.OK_200, type, content);
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return a status " + STATUS + " and(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void calling_on_will_return_a_status_and_(Guard guard, Method method, String path, long delay,
                                                     HttpStatusCode status, Type type, String content) {
        calling_on_will_return_a_status_and(guard, method, path, delay, status, type, content);
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return a status " + STATUS + "$")
    public void calling_will_return(Guard guard, Method method, String path, long delay, HttpStatusCode status) {
        calling_on_will_return_a_status_and(guard, method, path, delay, status, null, null);
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return a status " + STATUS + " and(?: " + A + TYPE + ")?:$")
    public void calling_on_will_return_a_status_and(Guard guard, Method method, String path, long delay,
                                                    HttpStatusCode status, Type type, String content) {
        guard.in(objects, () -> {
            Response response;
            if (Response.class.equals(type)) {
                response = Mapper.read(objects.resolve(content), Response.class);
            } else {
                String typeString = getTypeString(type, content);
                String contentType = switch (typeString) {
                    case "java.util.Map", "java.util.List" -> "application/json";
                    default -> "text/plain";
                };
                response = Response.builder()
                        .status(status.name())
                        .delay(delay)
                        .headers(Map.of("Content-Type", contentType))
                        .body(Interaction.Body.builder()
                                .payload(content)
                                .type(typeString)
                                .build())
                        .build();
            }
            url_is_mocked_as(path, Interaction.builder()
                    .request(Request.builder()
                            .method(method)
                            .build())
                    .response(List.of(response))
                    .build(), Comparison.CONTAINS);
        });
    }

    @NotNull
    private String getTypeString(Type type, String content) {
        return ofNullable(type).map(Type::getTypeName).orElseGet(() -> {
            if (content == null || Mapper.firstNonWhitespaceCharacterIs(content, '<')) {
                return "String";
            }
            return Mapper.isList(content) ? "java.util.List" : "java.util.Map";
        });
    }

    @Given(THAT + GUARD + QUOTED_CONTENT + " is mocked as" + COMPARING_WITH + ":$")
    public void url_is_mocked_as(Guard guard, String path, Comparison comparison, String content) {
        guard.in(objects, () -> {
            Interaction interaction = read(objects.resolve(content), Interaction.class);
            url_is_mocked_as(path, interaction, comparison);
        });
    }

    public void url_is_mocked_as(String path, Interaction interaction, Comparison comparison) {
        String mocked = mocked(objects.resolve(path));
        Matcher uri = match(mocked);
        add_mock(interaction.request.toHttpRequestIn(objects, uri, true), request -> {
            interaction.consumptionIndex++;

            String queryParamPattern = ofNullable(uri.group(5)).filter(s -> !s.isEmpty()).map(s -> "?" + toQueryString(toParameters(s, false))).orElse("");
            Pattern urlPattern = Pattern.compile(escapeBrackets(uri.group(4) + queryParamPattern));
            objects.add("_request", request);

            AtomicInteger consumptionSum = new AtomicInteger();
            Response responseForCall = interaction.response.stream()
                    .reduce(null,
                            (storedResponse, response) -> {
                                Response responseCandidate = response.consumptions == 0 || consumptionSum.addAndGet(response.consumptions) >= interaction.consumptionIndex ? response : null;
                                return storedResponse == null ? responseCandidate : storedResponse;
                            }
                    );
            if (responseForCall == null) {
                responseForCall = interaction.response.get(0);
            }

            Object responsePayload = responseForCall.body.payload;
            Matcher matcher = null;
            if (responsePayload != null) {
                boolean responseIsString = responsePayload instanceof String;
                String responsePayloadAsJson = responseIsString
                        ? (String) responsePayload
                        : Mapper.toJson(responsePayload);

                String url = request.getPath().getValue() + toQueryString(request.getQueryStringParameterList());
                matcher = urlPattern.matcher(url);
                if (matcher.matches() && matcher.groupCount() > 0) {
                    try {
                        String finalResponsePayload = matcher.replaceAll(responsePayloadAsJson);
                        responsePayload = responseIsString
                                ? finalResponsePayload
                                : Mapper.read(finalResponsePayload, responsePayload.getClass());
                        // we need to capture the path params for them to be available in the handlesbar template
                        Pattern pathPattern = Pattern.compile(uri.group(4));
                        Matcher pathMatcher = pathPattern.matcher(request.getPath().getValue());
                        if (pathMatcher.matches()) {
                            for (int i = 1; i <= pathMatcher.groupCount(); i++) {
                                request.withPathParameter("param" + i, pathMatcher.group(i));
                            }
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e); // let's warn in the test logs and continue
                    }
                }
            }
            return Response.builder()
                    .headers(new HashMap<>(responseForCall.headers))
                    .delay(responseForCall.delay)
                    .status(responseForCall.status)
                    .body(Interaction.Body.builder()
                            .type(responseForCall.body.type)
                            .payload(responsePayload)
                            .build())
                    .build().toHttpResponseIn(objects, matcher);
        }, comparison);
    }

    @When(THAT + GUARD + "(" + A_USER + ")" + SEND + " (?:on )?" + QUOTED_CONTENT + "(?: with)?(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void send_(Guard guard, String user, Method method, String path, Type type, String content) {
        send(guard, user, method, path, type, content);
    }

    @When(THAT + GUARD + "(" + A_USER + ")" + SEND + " (?:on )?" + QUOTED_CONTENT + "(?: with)?(?: " + A + TYPE + ")?:$")
    public void send(Guard guard, String user, Method method, String path, Type type, String unresolvedContent) {
        guard.in(objects, () -> {
            String content = objects.resolve(unresolvedContent);
            Request request;
            if (Request.class.equals(type)) {
                request = read(objects.resolve(content), Request.class);
            } else {
                request = Request.builder()
                        .body(Interaction.Body.builder()
                                .type(getTypeString(type, content))
                                .payload(content)
                                .build())
                        .build();
            }
            send(user, path, request.toBuilder().method(method).build());
        });
    }

    @When(THAT + GUARD + "(" + A_USER + ")sends? on " + QUOTED_CONTENT + ":$")
    public void send(Guard guard, String user, String path, String content) {
        guard.in(objects, () -> {
            Interaction.Request request = read(objects.resolve(content), Interaction.Request.class);
            if (Optional.ofNullable(request.headers.get("Content-Encoding")).map(encoding -> encoding.contains("gzip")).orElse(false)) {
                request = toRequestWithGzipBody(request);
            }

            send(user, path, request);
        });
    }

    private static Request toRequestWithGzipBody(Request request) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            String payload;
            if (request.body.payload instanceof String strPayload) {
                payload = strPayload;
            } else {
                payload = Mapper.toJson(request.body.payload);
            }

            gzipOutputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }

        request = Request.builder()
                .body(Interaction.Body.builder()
                        .type(byte[].class.getTypeName())
                        .payload(byteArrayOutputStream.toByteArray())
                        .build())
                .headers(request.headers)
                .method(request.method)
                .build();
        return request;
    }

    public void send(String user, String path, Request request) {
        try {
            objects.add("_response", Response.fromResponse(request.send(as(user), rewrite(target(objects.resolve(path))), objects)));
        } catch (Exception e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    private String rewrite(String path) {
        if (path.startsWith("/")) {
            return relativeUrlRewriter.apply(path);
        }
        return path;
    }

    @Then(THAT + GUARD + "(" + A_USER + ")sending on " + QUOTED_CONTENT + " receives" + COMPARING_WITH + ":$")
    public void send_and_assert(Guard guard, String user, String path, Comparison comparison, String content) {
        guard.in(objects, () -> {
            String interactionStr = objects.resolve(content);
            Interaction interaction = Mapper.read(interactionStr, Interaction.class);
            send(user, path, interaction.request);
            comparison.compare(objects.get("_response"), Mapper.read(interactionStr, Map.class).get("response"));
        });
    }

    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " returns a status " + STATUS + "$")
    public void call_and_assert(Guard guard, String user, Method method, String path, HttpStatusCode status) {
        guard.in(objects, () -> {
            call(always(), user, method, path);
            we_receive_a_status(always(), status);
        });
    }

    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " (?:returns|receives) a status " + STATUS + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void call_and_assert_(Guard guard, String user, Method method, String path, HttpStatusCode status, Comparison comparison, Type type, String content) {
        call_and_assert(guard, user, method, path, status, comparison, type, content);
    }

    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " (?:returns|receives) a status " + STATUS + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void call_and_assert(Guard guard, String user, Method method, String path, HttpStatusCode status, Comparison comparison, Type type, String content) {
        guard.in(objects, () -> {
            call(always(), user, method, path);
            we_receive_a_status_and(always(), status, comparison, type, content);
        });
    }

    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " (?:returns|receives)" + COMPARING_WITH + "(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void call_and_assert_(Guard guard, String user, Method method, String path, Comparison comparison, Type type, String content) {
        call_and_assert(guard, user, method, path, comparison, type, content);
    }

    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " (?:returns|receives)" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void call_and_assert(Guard guard, String user, Method method, String path, Comparison comparison, Type type, String content) {
        guard.in(objects, () -> {
            call(always(), user, method, path);
            we_receive(always(), comparison, type, content);
        });
    }

    @When(THAT + GUARD + "(" + A_USER + ")" + CALL + " (?:on )?" + QUOTED_CONTENT + "$")
    public void call(Guard guard, String user, Method method, String path) {
        guard.in(objects, () -> {
            try {
                objects.add("_response", Response.fromResponse(as(user).request(method.name(), rewrite(target(objects.resolve(path))))));
            } catch (Exception e) {
                throw new AssertionError(e.getMessage(), e);
            }
        });
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? a status " + STATUS + "$")
    public void we_receive_a_status(Guard guard, HttpStatusCode status) {
        guard.in(objects, () -> {
            Response response = objects.get("_response");
            withFailMessage(() -> assertThat(response.status).isEqualTo(status.name()), () -> """
                    Expected status code <%s> but was <%s>
                    payload:
                    %s
                    """.formatted(status, getHttpStatusCode(response.status), response.body.payload));
        });
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)?" + COMPARING_WITH + "(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void we_receive_(Guard guard, Comparison comparison, Type type, String content) {
        we_receive(guard, comparison, type, content);
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)?" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void we_receive(Guard guard, Comparison comparison, Type type, String content) {
        guard.in(objects, () -> {
            Response response = objects.get("_response");
            String payload = objects.resolve(content);
            if (Response.class.equals(type)) {
                Map<String, Object> expected = Mapper.read(objects.resolve(payload));
                Object statusValue = expected.get("status");
                if (statusValue instanceof String statusStr) {
                    expected.put("status", getHttpStatusCode(statusStr).name());
                }
                comparison.compare(response, expected);
            } else {
                comparison.compare(response.body.payload, payload);
            }
        });
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? a status " + STATUS + " and a (?:" + TYPE + " )?" + VARIABLE + "$")
    public void we_receive_a_status_and_we_save_the_body_as(Guard guard, HttpStatusCode status, Type type, String variable) {
        we_receive_a_status(guard, status);
        we_save_the_payload_as(guard, type, variable);
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? a (?:" + TYPE + " )?" + VARIABLE + "$")
    public void we_save_the_payload_as(Guard guard, Type type, String variable) {
        guard.in(objects, () ->
                objects.add(variable, objects.resolvePossiblyTypedObject(type, objects.<Response>get("_response").body.payload)));
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? a status " + STATUS + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void we_receive_a_status_and_(Guard guard, HttpStatusCode status, Comparison comparison, Type type, String content) {
        we_receive_a_status_and(guard, status, comparison, type, content);
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? a status " + STATUS + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void we_receive_a_status_and(Guard guard, HttpStatusCode status, Comparison comparison, Type type, String content) {
        we_receive_a_status(guard, status);
        we_receive(guard, comparison, type, content);
    }

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received(?: " + VERIFICATION + ")? " + COUNT_OR_VARIABLE + " " + CALL + "(?: " + VARIABLE + ")?$")
    public void mockserver_has_received(Guard guard, String path, String verification, String countAsString, Method method, String variable) {
        guard.in(objects, () -> {
            int expectedNbCalls = objects.getCount(countAsString);
            VerificationTimes times = ofNullable(verification)
                    .<Function<Integer, VerificationTimes>>map(v -> switch (v) {
                        case "at least" -> VerificationTimes::atLeast;
                        case "at most" -> VerificationTimes::atMost;
                        default -> VerificationTimes::exactly;
                    })
                    .orElse(VerificationTimes::exactly)
                    .apply(expectedNbCalls);
            Matcher uri = match(mocked(objects.resolve(path)));
            HttpRequest httpRequest = request().withMethod(method.name()).withPath(uri.group(4)).withQueryStringParameters(toParameters(uri.group(5)));

            MockFaster.verify(httpRequest, times);

            if (variable != null) {
                List<HttpRequest> requests = retrieveRecordedRequests(httpRequest);
                if (expectedNbCalls == 1) {
                    objects.add(variable, requests.get(0));
                } else {
                    objects.add(variable, requests);
                }
            }
        });
    }

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received a " + SEND + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void mockserver_has_received_a_call_and_(Guard guard, String path, Method method, Comparison comparison, Type type, String content) {
        mockserver_has_received_a_call_and(guard, path, method, comparison, type, content);
    }

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received" + COMPARING_WITH + ":$")
    public void mockserver_has_received_a_call_and_(Guard guard, String path, Comparison comparison, String content) {
        mockserver_has_received(guard, comparison, path, readAsAListOf(objects.resolve(content), Map.class).stream().map(Mapper::toJson).map(Interaction::wrapAsInteractionJson).collect(Collectors.joining(",", "[", "]")));
    }

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received a " + SEND + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void mockserver_has_received_a_call_and(Guard guard, String path, Method method, Comparison comparison, Type type, String content) {
        Request request;
        if (Request.class.equals(type)) {
            request = read(objects.resolve(content), Request.class);
        } else {
            request = Request.builder().body(Interaction.Body.builder().payload(objects.resolve(content)).build()).build();
        }
        Matcher uri = match(mocked(objects.resolve(path)));
        HttpRequest httpRequest = request.toHttpRequestIn(objects, uri, false).clone().withMethod(method.name());
        guard.in(objects, () -> assertHasReceived(comparison, httpRequest));
    }

    @Then(THAT + GUARD + "the interactions? on " + QUOTED_CONTENT + " (?:were|was)" + COMPARING_WITH + ":$")
    public void the_interactions_were(Guard guard, String path, Comparison comparison, Object content) {
        mockserver_has_received(guard, comparison, path, objects.resolve(content));
    }

    private void mockserver_has_received(Guard guard, Comparison comparison, String path, String expectedInteractionsStr) {
        Matcher uri = match(mocked(objects.resolve(path)));
        guard.in(objects, () -> {
            List<Interaction> expectedInteractions = Mapper.readAsAListOf(expectedInteractionsStr, Interaction.class);
            List<Interaction> recordedInteractions = expectedInteractions
                    .stream()
                    .map(interaction -> interaction.request.toHttpRequestIn(objects, uri, false).clone().withHeaders(Collections.emptyList()).withBody((Body<?>) null))
                    .map(MockFaster::retrieveRequestResponses)
                    .flatMap(Collection::stream)
                    .collect(toMap(e -> e.getHttpRequest().getLogCorrelationId(), identity(), (h1, h2) -> h1))
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(LogEventRequestAndResponse::getTimestamp))
                    .map(logEventRequestAndResponse -> Interaction.builder()
                            .request(Request.fromHttpRequest((HttpRequest) logEventRequestAndResponse.getHttpRequest()))
                            .response(List.of(Response.fromHttpResponse(logEventRequestAndResponse.getHttpResponse())))
                            .build())
                    .collect(toList());

            List<Map> parsedExpectedInteractions = Mapper.readAsAListOf(expectedInteractionsStr, Map.class);
            parsedExpectedInteractions.forEach(expectedInteraction -> expectedInteraction.computeIfPresent("response", (key, response) -> response instanceof List ? response : Collections.singletonList(response)));
            comparison.compare(recordedInteractions, Mapper.toJson(parsedExpectedInteractions));
        });
    }

    @Then(THAT + GUARD + "we received a request on these paths" + COMPARING_WITH + ":$")
    public void we_received_a_request_on_paths(Guard guard, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            List<HttpRequest> expectedHttpRequests = readAsAListOf(objects.resolve(content), String.class).stream()
                    .map(expectedRequestPath -> {
                        Matcher withoutFlagInUriMatch = match(mocked(expectedRequestPath.replaceFirst("^\\?e ", "")));
                        String uriGroup = withoutFlagInUriMatch.group(4);
                        return request()
                                .withPath(expectedRequestPath.startsWith("?e") ? "?e " + uriGroup : uriGroup)
                                .withQueryStringParameters(toParameters(withoutFlagInUriMatch.group(5)));
                    }).toList();

            List<HttpRequest> receivedHttpRequests = retrieveRecordedRequests(new HttpRequest());
            if(!EnumSet.of(Comparison.CONTAINS_ONLY_IN_ORDER, Comparison.CONTAINS_ONLY).contains(comparison)){
                receivedHttpRequests = receivedHttpRequests.stream()
                        .filter(httpRequest -> expectedHttpRequests.stream().anyMatch(expectedRequest -> {
                            try {
                                Asserts.equals(httpRequest.getPath().toString(), expectedRequest.getPath().toString());
                                Asserts.equals(httpRequest.getQueryStringParameterList(), expectedRequest.getQueryStringParameterList());
                                return true;
                            } catch (AssertionError e) {
                                return false;
                            }
                        })).toList();
            }

            comparison.compare(
                    receivedHttpRequests.stream().map(httpRequest -> httpRequest.getPath().toString()).toList(),
                    expectedHttpRequests.stream().map(httpRequest -> httpRequest.getPath().toString()).toList()
            );
            comparison.compare(
                    receivedHttpRequests.stream().map(HttpRequest::getQueryStringParameterList).toList(),
                    expectedHttpRequests.stream().map(HttpRequest::getQueryStringParameterList).toList()
            );
        });
    }

    @And(THAT + GUARD + QUOTED_CONTENT + " has not been called$")
    public void mockserver_has_not_been_called_on(Guard guard, String path) {
        guard.in(objects, () -> {
            List<HttpRequest> requests = retrieveRecordedRequests(request()
                    .withPath(mocked(objects.resolve(path)))
                    .withContentType(APPLICATION_JSON));
            assertThat(requests).isEmpty();
        });
    }

    @After
    public void after() {
        if (doNotAllowUnhandledRequests) {
            List<LogEventRequestAndResponse> requestAndResponses = retrieveRequestResponses(request());
            String notFoundMessageRegex = "Not Found(?: Again!)?";
            Set<HttpRequest> unhandledRequests = requestAndResponses
                    .stream()
                    .filter(requestAndResponse -> Optional.ofNullable(requestAndResponse.getHttpResponse().getReasonPhrase()).orElse("").matches(notFoundMessageRegex))
                    .map(requestAndResponse -> (HttpRequest) requestAndResponse.getHttpRequest())
                    .collect(Collectors.toSet());
            // we make a second pass, the calls might have been handled later on
            requestAndResponses.stream()
                    .filter(requestAndResponse -> !Optional.ofNullable(requestAndResponse.getHttpResponse().getReasonPhrase()).orElse("").matches(notFoundMessageRegex))
                    .forEach(requestAndResponse -> unhandledRequests.remove(requestAndResponse.getHttpRequest()));
            // then we ignore allowed unhandled requests
            allowedUnhandledRequests.forEach(allowedUnhandledRequest -> {
                List<HttpRequest> requests = retrieveRecordedRequests(allowedUnhandledRequest.clone().withHeaders(Collections.emptyList()).withBody((Body<?>) null));
                requests.stream().filter(recorded -> {
                    try {
                        compareHeaders(Comparison.CONTAINS, recorded, allowedUnhandledRequest.getHeaderList());
                        compareBodies(Comparison.CONTAINS, recorded, allowedUnhandledRequest.getBodyAsString());
                    } catch (Throwable e) {
                        return false;
                    }
                    return true;
                }).toList().forEach(unhandledRequests::remove);
            });
            withFailMessage(() -> assertThat(unhandledRequests).isEmpty(), () -> "unhandled requests: %s".formatted(unhandledRequests));
        }
    }

    public void addHeader(String username, String name, String value) {
        headersByUsername.computeIfAbsent(username, s -> new ArrayList<>()).add(Pair.of(name, value));
    }

    private RequestSpecification as(String user) {
        RequestSpecification request = given();
        user = user != null ? user.trim() : null;
        if (headersByUsername.containsKey(user)) {
            for (Pair<String, String> header : headersByUsername.get(user)) {
                request = request.header(header.getKey(), header.getValue());
            }
        }
        return request;
    }

    public static HttpStatusCode getHttpStatusCode(String value) {
        if (value.matches("\\d+")) {
            // code as int
            return HttpStatusCode.code(Integer.parseInt(value));
        }
        try {
            // code as OK
            return HttpStatusCode.code((int) unchecked(() -> Fields.getField(HttpStatus.class, "SC_" + value).get(null)));
        } catch (Exception e) {
            // code as OK_200
            return HttpStatusCode.valueOf(value);
        }
    }
}
