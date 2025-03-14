package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import com.decathlon.tzatziki.utils.Interaction.Request;
import com.decathlon.tzatziki.utils.Interaction.Response;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.CountMatchingMode;
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static com.decathlon.tzatziki.utils.Asserts.withFailMessage;
import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Guard.always;
import static com.decathlon.tzatziki.utils.HttpUtils.*;
import static com.decathlon.tzatziki.utils.Mapper.read;
import static com.decathlon.tzatziki.utils.Mapper.readAsAListOf;
import static com.decathlon.tzatziki.utils.Method.*;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class HttpSteps {

    public static final String STATUS = "([A-Z_]+[A-Z]|\\d+|[A-Z_]+_\\d+)";
    public static final WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort().globalTemplating(true).extensionScanningEnabled(true));
    private boolean doNotAllowUnhandledRequests = true;
    private final Set<RequestPatternBuilder> allowedUnhandledRequests = new HashSet<>();
    private final Map<String, List<Pair<String, String>>> headersByUsername = new LinkedHashMap<>();
    private UnaryOperator<String> relativeUrlRewriter = UnaryOperator.identity();
    public static final Set<String> MOCKED_PATHS = new LinkedHashSet<>();

    static {
        DynamicTransformers.register(Method.class, Method::of);
        DynamicTransformers.register(HttpStatusCode.class, HttpSteps::getHttpStatusCode);

        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        localPort = wireMockServer.port();
    }


    private final ObjectSteps objects;

    public HttpSteps(ObjectSteps objects) {
        this.objects = objects;

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

    @Before(order = -1) // just for this instance to be created
    public void before() {
        wireMockServer.resetAll();
        MOCKED_PATHS.clear();
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return(?: " + A + TYPE + ")?:$")
    public void calling_on_will_return(Guard guard, Method method, String path, long delay, Type type, String content) {
        calling_on_will_return_a_status_and(guard, method, path, delay, HttpStatusCode.OK_200, type, content);
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
                        .consumptions(1)
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

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received" + COMPARING_WITH + ":$")
    public void mockserver_has_received_a_call_and_(Guard guard, String path, Comparison comparison, String content) {
        mockserver_has_received(guard, comparison, path, readAsAListOf(objects.resolve(content), Map.class).stream().map(Mapper::toJson).map(Interaction::wrapAsInteractionJson).collect(Collectors.joining(",", "[", "]")));
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return a status " + STATUS + " and(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void calling_on_will_return_a_status_and_(Guard guard, Method method, String path, long delay,
                                                     HttpStatusCode status, Type type, String content) {
        calling_on_will_return_a_status_and(guard, method, path, delay, status, type, content);
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

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void calling_on_will_return_(Guard guard, Method method, String path, long delay, Type type, String content) {
        calling_on_will_return(guard, method, path, delay, type, content);
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)?" + COMPARING_WITH + "(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void we_receive_(Guard guard, Comparison comparison, Type type, String content) {
        we_receive(guard, comparison, type, content);
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

    @Then(THAT + GUARD + "(" + A_USER + ")sending on " + QUOTED_CONTENT + " receives" + COMPARING_WITH + ":$")
    public void send_and_assert(Guard guard, String user, String path, Comparison comparison, String content) {
        guard.in(objects, () -> {
            String interactionStr = objects.resolve(content);
            Interaction interaction = Mapper.read(interactionStr, Interaction.class);
            send(user, path, interaction.request);
            comparison.compare(objects.get("_response"), Mapper.read(interactionStr, Map.class).get("response"));
        });
    }

    @When(THAT + GUARD + "(" + A_USER + ")" + SEND + " (?:on )?" + QUOTED_CONTENT + "(?: with)?(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void send_(Guard guard, String user, Method method, String path, Type type, String content) {
        send(guard, user, method, path, type, content);
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? a status " + STATUS + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void we_receive_a_status_and(Guard guard, HttpStatusCode status, Comparison comparison, Type type, String content) {
        we_receive_a_status(guard, status);
        we_receive(guard, comparison, type, content);
    }

    public void url_is_mocked_as(String path, Interaction interaction, Comparison comparison) {
        String mocked = mocked(objects.resolve(path));

        String scenarioName = "Scenario for " + path;
        String initialState = Scenario.STARTED;

        for (int responseIndex = 1; responseIndex <= interaction.response.size(); responseIndex++) {
            for (int consumptionIndex = 1; consumptionIndex <= interaction.response.get(responseIndex - 1).consumptions; consumptionIndex++) {
                Response response = interaction.response.get(responseIndex - 1);
                String stateName = responseIndex == 1 && consumptionIndex == 1 ? initialState : "State " + responseIndex + "_" + consumptionIndex;

                String nextStateName = consumptionIndex == response.consumptions ? "State " + (responseIndex + 1) + "_" + 1 : "State " + responseIndex + "_" + (consumptionIndex + 1);

                nextStateName = responseIndex == interaction.response.size() && consumptionIndex == response.consumptions ? null : nextStateName;

                MappingBuilder request = getRequest(interaction, response, match(mocked), scenarioName, stateName, nextStateName, comparison);

                wireMockServer.stubFor(request);
            }
        }
    }

    private MappingBuilder getRequest(Interaction interaction, Response response, Matcher uri, String scenarioName, String stateName, String nextStateName, Comparison comparison) {
        MappingBuilder request = interaction.request.toMappingBuilder(objects, uri, comparison);
        ResponseDefinitionBuilder responseDefinition = response.toResponseDefinitionBuilder(objects, uri);

        request.inScenario(scenarioName)
                .whenScenarioStateIs(stateName)
                .willReturn(responseDefinition)
                .willSetStateTo(nextStateName);
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

    @Given(THAT + GUARD + "we allow unhandled mocked requests (?:on )?" + QUOTED_CONTENT + ":$")
    public void we_allow_unhandled_mocked_requests(Guard guard, String path, String content) {
        guard.in(objects, () -> {
            String mocked = mocked(objects.resolve(path));
            Matcher uri = match(mocked);
            allowedUnhandledRequests.add(read(objects.resolve(content), Request.class).toRequestPatternBuilder(objects, uri, Comparison.CONTAINS));
        });
    }

    @Given(THAT + GUARD + "we allow unhandled mocked requests " + CALLING + " (?:on )?" + QUOTED_CONTENT + "$")
    public void we_allow_unhandled_mocked_requests(Guard guard, Method method, String path) {
        guard.in(objects, () -> {
            String mocked = mocked(objects.resolve(path));
            Matcher uri = match(mocked);
            allowedUnhandledRequests.add(Request.builder().method(method).build().toRequestPatternBuilder(objects, uri, Comparison.CONTAINS));
        });
    }

    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " (?:returns|receives)" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void call_and_assert(Guard guard, String user, Method method, String path, Comparison comparison, Type type, String content) {
        guard.in(objects, () -> {
            call(always(), user, method, path);
            we_receive(always(), comparison, type, content);
        });
    }

    @Given(THAT + GUARD + QUOTED_CONTENT + " is mocked as" + COMPARING_WITH + ":$")
    public void url_is_mocked_as(Guard guard, String path, Comparison comparison, String content) {
        guard.in(objects, () -> {
            Interaction interaction = read(objects.resolve(content), Interaction.class);
            url_is_mocked_as(path, interaction, comparison);
        });
    }

    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " returns a status " + STATUS + "$")
    public void call_and_assert(Guard guard, String user, Method method, String path, HttpStatusCode status) {
        guard.in(objects, () -> {
            call(always(), user, method, path);
            we_receive_a_status(always(), status);
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

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received(?: " + VERIFICATION + ")? " + COUNT_OR_VARIABLE + " " + CALL + "(?: " + VARIABLE + ")?$")
    public void mockserver_has_received(Guard guard, String path, String verification, String countAsString, Method method, String variable) {
        guard.in(objects, () -> {
            int expectedNbCalls = objects.getCount(countAsString);
            CountMatchingMode countMatchingMode = ofNullable(verification)
                    .map(v -> switch (v) {
                        case "at least" -> CountMatchingStrategy.GREATER_THAN_OR_EQUAL;
                        case "at most" -> CountMatchingStrategy.LESS_THAN_OR_EQUAL;
                        default -> CountMatchingStrategy.EQUAL_TO;
                    })
                    .orElse(CountMatchingStrategy.EQUAL_TO);
            Matcher uri = match(mocked(objects.resolve(path)));

            RequestPatternBuilder requestPatternBuilder = RequestPatternBuilder.newRequestPattern(RequestMethod.fromString(method.name()), urlPathMatching(uri.group(4)));
            List<Pair<String, String>> valuePairsQueryParams = HttpUtils.parseQueryParams(uri.group(5), false);
            valuePairsQueryParams.forEach(pair -> requestPatternBuilder.withQueryParam(pair.getKey(), matching(pair.getValue())));

            verify(new CountMatchingStrategy(countMatchingMode, expectedNbCalls), requestPatternBuilder);

            if (variable != null) {
                List<ServeEvent> serveEvents = wireMockServer.getAllServeEvents().stream().filter(serveEvent -> RequestPattern.thatMatch(requestPatternBuilder.build()).test(serveEvent.getRequest()))
                        .sorted(Comparator.comparing(serveEvent -> serveEvent.getRequest().getLoggedDate()))
                        .toList();

                List<Interaction> recordedInteractions = serveEvents.stream().map(serveEvent -> Interaction.builder()
                                .request(Request.fromLoggedRequest(serveEvent.getRequest()))
                                .response(List.of(Response.fromLoggedResponse(serveEvent.getResponse())))
                                .build())
                        .collect(toList());
                if (expectedNbCalls == 1) {
                    objects.add(variable, recordedInteractions.get(0));
                } else {
                    objects.add(variable, recordedInteractions);
                }
            }
        });
    }

    @Then(THAT + GUARD + "the interactions? on " + QUOTED_CONTENT + " (?:were|was)" + COMPARING_WITH + ":$")
    public void the_interactions_were(Guard guard, String path, Comparison comparison, Object content) {
        mockserver_has_received(guard, comparison, path, objects.resolve(content));
    }

    private void mockserver_has_received(Guard guard, Comparison comparison, String path, String expectedInteractionsStr) {
        Matcher uri = match(mocked(objects.resolve(path)));
        guard.in(objects, () -> {
            List<Interaction> expectedInteractions = Mapper.readAsAListOf(expectedInteractionsStr, Interaction.class);
//            if (expectedInteractions.size() == 1) {
//                RequestPatternBuilder request = expectedInteractions.get(0).request.toRequestPatternBuilder(objects, uri, comparison);
//                verify(request);
//                return;
//            }
//TODO think if we use the internal wiremock or not, but not handling flag

            RequestPatternBuilder requestPatternBuilder = new RequestPatternBuilder(RequestMethod.ANY, urlPathMatching(uri.group(4)));
            List<Pair<String, String>> valuePairsQueryParams = parseQueryParams(uri.group(5), false);
            valuePairsQueryParams.forEach(pair -> requestPatternBuilder.withQueryParam(pair.getKey(), matching(pair.getValue())));

            List<ServeEvent> serveEvents = wireMockServer.getAllServeEvents().stream().filter(serveEvent -> RequestPattern.thatMatch(requestPatternBuilder.build()).test(serveEvent.getRequest()))
                    .sorted(Comparator.comparing(serveEvent -> serveEvent.getRequest().getLoggedDate()))
                    .toList();

            List<Interaction> recordedInteractions = serveEvents.stream().map(serveEvent -> Interaction.builder()
                            .request(Request.fromLoggedRequest(serveEvent.getRequest()))
                            .response(List.of(Response.fromLoggedResponse(serveEvent.getResponse())))
                            .build())
                    .collect(toList());

            List<Map> parsedExpectedInteractions = Mapper.readAsAListOf(expectedInteractionsStr, Map.class);
            parsedExpectedInteractions.forEach(expectedInteraction -> expectedInteraction.computeIfPresent("response", (key, response) -> response instanceof List ? response : Collections.singletonList(response)));
            comparison.compare(recordedInteractions, Mapper.toJson(parsedExpectedInteractions));
        });
    }

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received a " + SEND + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void mockserver_has_received_a_call_and(Guard guard, String path, Method method, Comparison comparison, Type type, String content) {
        mockserver_has_received_a_call_and(guard, path, method, comparison, type, content, null);
    }

    private void mockserver_has_received_a_call_and(Guard guard, String path, Method method, Comparison comparison, Type type, String content, Integer count) {
        Request request;
        if (Request.class.equals(type)) {
            request = read(objects.resolve(content), Request.class);
        } else {
            String typeString = getTypeString(type, content);
            request = Request.builder().body(Interaction.Body.builder().payload(objects.resolve(content)).type(typeString).build()).build();
        }
        if (method != null) {
            request = request.toBuilder().method(method).build();
        }

        Matcher uri = match(mocked(objects.resolve(path)));
        RequestPatternBuilder requestPatternBuilder = request.toRequestPatternBuilder(objects, uri, comparison, false, false);

        Map<String, String> expectedHeaders = request.headers;
        String expectedBody = request.body.toString(objects);
        guard.in(objects, () -> assertHasReceived(comparison, requestPatternBuilder, expectedHeaders, expectedBody, count));
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return a status " + STATUS + "$")
    public void calling_will_return(Guard guard, Method method, String path, long delay, HttpStatusCode status) {
        calling_on_will_return_a_status_and(guard, method, path, delay, status, null, null);
    }

    @Given(THAT + GUARD + "we allow unhandled mocked requests$")
    public void we_allow_unhandled_mocked_requests(Guard guard) {
        guard.in(objects, () -> this.doNotAllowUnhandledRequests = false);
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

    @And(THAT + GUARD + QUOTED_CONTENT + " has not been called$")
    public void mockserver_has_not_been_called_on(Guard guard, String path) {
        mockserver_has_received_a_call_and(guard, path, null, Comparison.CONTAINS, null, null, 0);
    }

    //TODO SEE IF needed with wiremock
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

    @After
    public void after() {
        if (doNotAllowUnhandledRequests) {
            List<ServeEvent> unhandledRequests = wireMockServer.getServeEvents(ServeEventQuery.ALL_UNMATCHED).getRequests();
            List<ServeEvent> forbiddenUnhandledRequests = unhandledRequests.stream().
                    filter(serveEvent -> allowedUnhandledRequests.stream()
                            .noneMatch(allowedUnhandledRequest -> RequestPattern.thatMatch(allowedUnhandledRequest.build()).test(serveEvent.getRequest()))).toList();
            withFailMessage(() -> assertThat(forbiddenUnhandledRequests).isEmpty(), () -> "unhandled requests: %s".formatted(unhandledRequests));
        }
    }


    public static void assertHasReceived(Comparison comparison, RequestPatternBuilder requestPatternBuilder, Map<String, String> expectedHeaders, String expectedBody, Integer count) {
        if (count != null) {
            wireMockServer.verify(count, requestPatternBuilder);
            return;
        }
        List<ServeEvent> serveEvents = wireMockServer.getAllServeEvents().stream().filter(serveEvent -> RequestPattern.thatMatch(requestPatternBuilder.build()).test(serveEvent.getRequest()))
                .sorted(Comparator.comparing(serveEvent -> serveEvent.getRequest().getLoggedDate()))
                .toList();

        AtomicReference<Throwable> throwable = new AtomicReference<>();
        serveEvents.stream().filter(recorded -> {
                    try {
                        compareHeaders(comparison, HttpUtils.asMap(recorded.getRequest().getHeaders().all()), expectedHeaders);
                        compareBodies(comparison, recorded.getRequest().getBodyAsString(), expectedBody);
                    } catch (Throwable e) {
                        throwable.set(e);
                        return false;
                    }
                    return true;
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("""
                        Request not found at least once, expected: %s but was: %s
                        """.formatted(requestPatternBuilder.toString(), serveEvents), throwable.get()));
    }

    public static void compareHeaders(Comparison comparison, Map<String, String> requestHeaders, Map<String, String> headers) {
        if (!headers.isEmpty()) {
            comparison.compare(requestHeaders, headers);
        }
    }

    public static void compareBodies(Comparison comparison, String actualBody, String body) {
        if (StringUtils.isNotBlank(body)) {
            comparison.compare(actualBody, body);
        }
    }

    public void send(String user, String path, Request request) {
        try {
            objects.add("_response", Response.fromResponse(request.send(as(user), rewrite(target(objects.resolve(path))), objects)));
        } catch (Exception e) {
            throw new AssertionError(e.getMessage(), e);
        }
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

    public void addHeader(String username, String name, String value) {
        headersByUsername.computeIfAbsent(username, s -> new ArrayList<>()).add(Pair.of(name, value));
    }

    private String rewrite(String path) {
        if (path.startsWith("/")) {
            return relativeUrlRewriter.apply(path);
        }
        return path;
    }

    public void setRelativeUrlRewriter(UnaryOperator<String> relativeUrlRewriter) {
        this.relativeUrlRewriter = relativeUrlRewriter;
    }

}
