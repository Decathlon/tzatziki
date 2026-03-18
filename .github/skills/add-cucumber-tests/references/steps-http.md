# User Provided Header
Tzatziki HTTP module reference.
- HttpSteps.java defines @Given/@When/@Then patterns for HTTP mocking, request/response assertions, and REST API testing.
- .feature files demonstrate valid HTTP step usage with YAML doc strings.
- Prefer YAML (`"""yml`) for request/response bodies.


# Directory Structure
```
tzatziki-http/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                HttpSteps.java
    test/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                LocalSteps.java
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                http.feature
```

# Files

## File: tzatziki-http/src/main/java/com/decathlon/tzatziki/steps/HttpSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.configuration.HttpConfigurationProperties;
import com.decathlon.tzatziki.jetty.JettyRequestLimitHttpServerFactory;
import com.decathlon.tzatziki.utils.*;
import com.decathlon.tzatziki.utils.Interaction.Request;
import com.decathlon.tzatziki.utils.Interaction.Response;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.*;
import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extensions;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.NearMiss;
import com.github.tomakehurst.wiremock.verification.diff.Diff;
import com.github.tomakehurst.wiremock.verification.notmatched.PlainTextStubNotMatchedRenderer;
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
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static com.decathlon.tzatziki.utils.Asserts.withFailMessage;
import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Guard.always;
import static com.decathlon.tzatziki.utils.HttpWiremockUtils.*;
import static com.decathlon.tzatziki.utils.Mapper.read;
import static com.decathlon.tzatziki.utils.Mapper.readAsAListOf;
import static com.decathlon.tzatziki.utils.Method.*;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.core.Options.ChunkedEncodingPolicy.BODY_FILE;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.allRequests;
import static io.restassured.RestAssured.given;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SuppressWarnings({
    "java:S100", // Allow method names with underscores for BDD steps.
    "java:S107"  // Methods with too many parameters are acceptable in BDD step definitions.
}) 
public class HttpSteps {

    public static final String STATUS = "([A-Z_]+[A-Z]|\\d+|[A-Z_]+_\\d+)";
    public static final WireMockServer wireMockServer = new WireMockServer(
            createWireMockConfiguration());
    private boolean doNotAllowUnhandledRequests = true;
    private final Set<RequestPatternBuilder> allowedUnhandledRequests = new HashSet<>();
    private final Map<String, List<Pair<String, String>>> headersByUsername = new LinkedHashMap<>();
    private UnaryOperator<String> relativeUrlRewriter = UnaryOperator.identity();
    public static final Set<String> MOCKED_PATHS = new LinkedHashSet<>();
    public static Integer localPort;
    public static boolean resetMocksBetweenTests = true;
    private static final PlainTextStubNotMatchedRenderer notMatchedRenderer = new PlainTextStubNotMatchedRenderer(Extensions.NONE);

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

    private static WireMockConfiguration createWireMockConfiguration() {
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig()
                .useChunkedTransferEncoding(BODY_FILE) // Don't use chunked transfer encoding for our mocked responses, preserving backward compatibility with MockServer behavior
                .globalTemplating(true)
                .extensions(new UrlPatternTransformer())
                .extensions(new ContentTypeTransformer())
                .extensions(new SplitHelperProviderExtension())
                .extensions(new CustomCallbackTransformer())
                .extensions(new JettyRequestLimitHttpServerFactory());

        config.port(HttpConfigurationProperties.getPortProperty());

        return config;
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
        if (resetMocksBetweenTests) {
            HttpUtils.reset();
        }
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return(?: " + A + TYPE + ")?:$")
    public void calling_on_will_return(Guard guard, Method method, String path, long delay, Type type, String content) {
        calling_on_will_return_a_status_and(guard, method, path, delay, HttpStatusCode.OK_200, type, content);
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return a status " + STATUS + " and(?: " + A + TYPE + ")?:$")
    public void calling_on_will_return_a_status_and(Guard guard, Method method, String path, long delay, HttpStatusCode status, Type type, String content) {
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
                response = Response.builder().status(status.name()).delay(delay).headers(Map.of("Content-Type", contentType)).consumptions(1).body(Interaction.Body.builder().payload(content).type(typeString).build()).build();
            }
            url_is_mocked_as(path, Interaction.builder().request(Request.builder().method(method).build()).response(List.of(response)).build(), Comparison.CONTAINS);
        });
    }

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received" + COMPARING_WITH + ":$")
    public void wiremock_has_received_a_call_and_(Guard guard, String path, Comparison comparison, String content) {
        wiremock_has_received(guard, comparison, path, readAsAListOf(objects.resolve(content), Map.class).stream().map(Mapper::toJson).map(Interaction::wrapAsInteractionJson).collect(Collectors.joining(",", "[", "]")));
    }

    @Given(THAT + GUARD + CALLING + " (?:on )?" + QUOTED_CONTENT + " will(?: take " + A_DURATION + " to)? return a status " + STATUS + " and(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void calling_on_will_return_a_status_and_(Guard guard, Method method, String path, long delay, HttpStatusCode status, Type type, String content) {
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
    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " (?:returns|receives)" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void call_and_assert(Guard guard, String user, Method method, String path, Comparison comparison, Type type, String content) {
        guard.in(objects, () -> {
            call(always(), user, method, path);
            we_receive(always(), comparison, type, content);
        });
    }
    
    @Then(THAT + GUARD + "(" + A_USER + ")?" + CALLING + " (?:on )?" + QUOTED_CONTENT + " (?:returns|receives)" + COMPARING_WITH + "(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void call_and_assert_(Guard guard, String user, Method method, String path, Comparison comparison, Type type, String content) {
        call_and_assert(guard, user, method, path, comparison, type, content);
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
        guard.in(objects, () -> objects.add(variable, objects.resolvePossiblyTypedObject(type, objects.<Response>get("_response").body.payload)));
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

    /**
     * Registers a sequence of mock responses for a given URL path in WireMock, using scenario and state
     * to control the order and consumption of responses.
     *
     * @param path        The URL path to be mocked.
     * @param interaction The interaction object containing the request and a list of responses.
     *                    Each response may be consumed multiple times (see {@code consumptions}).
     * @param comparison  The comparison logic to be used for matching requests.
     **/
    public void url_is_mocked_as(String path, Interaction interaction, Comparison comparison) {
        String mocked = mocked(objects.resolve(path));

        String scenarioName = interaction.request.method + "_" + path;
        String initialState = Scenario.STARTED;

        for (int responseIndex = 1; responseIndex <= interaction.response.size(); responseIndex++) {
            for (int consumptionIndex = 1; consumptionIndex <= interaction.response.get(responseIndex - 1).consumptions; consumptionIndex++) {
                Response response = interaction.response.get(responseIndex - 1);
                // The state in which this response is served
                String stateName = responseIndex == 1 && consumptionIndex == 1 ? initialState : "State " + responseIndex + "_" + consumptionIndex;
                // The state to transition to after serving this response
                String nextStateName = consumptionIndex == response.consumptions ? "State " + (responseIndex + 1) + "_" + 1 : "State " + responseIndex + "_" + (consumptionIndex + 1);
                // If this is the last response and last consumption, do not transition to a new state
                nextStateName = responseIndex == interaction.response.size() && consumptionIndex == response.consumptions ? null : nextStateName;
                MappingBuilder request = getRequest(interaction, response, match(mocked), scenarioName, stateName, nextStateName, comparison);
                wireMockServer.stubFor(request);
            }
        }
    }

    private MappingBuilder getRequest(Interaction interaction, Response response, Matcher uri, String scenarioName, String stateName, String nextStateName, Comparison comparison) {
        MappingBuilder request = interaction.request.toMappingBuilder(objects, uri, comparison);
        ResponseDefinitionBuilder responseDefinition = response.toResponseDefinitionBuilder(objects, uri);
        request.inScenario(scenarioName).whenScenarioStateIs(stateName).willReturn(responseDefinition).willSetStateTo(nextStateName);
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
                request = Request.builder().body(Interaction.Body.builder().type(getTypeString(type, content)).payload(content).build()).build();
            }
            send(user, path, request.toBuilder().method(method).build());
        });
    }

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received(?: " + VERIFICATION + ")? " + COUNT_OR_VARIABLE + " " + CALL + "(?: " + VARIABLE + ")?$")
    public void wiremock_has_received(Guard guard, String path, String verification, String countAsString, Method method, String variable) {
        guard.in(objects, () -> {
            int expectedNbCalls = objects.getCount(countAsString);
            CountMatchingMode countMatchingMode = getCountMatchingMode(verification);
            Matcher uri = match(mocked(objects.resolve(path)));

            RequestPatternBuilder requestPatternBuilder = Request.builder().method(method).build().toRequestPatternBuilder(objects, uri, null);
            List<ServeEvent> serveEvents = verifyAndGetServeEvents(new CountMatchingStrategy(countMatchingMode, expectedNbCalls), requestPatternBuilder);

            if (variable != null) {
                objects.add(variable, expectedNbCalls == 1 ? serveEvents.get(0) : serveEvents);
            }
        });
    }

    private CountMatchingMode getCountMatchingMode(String verification) {
        return ofNullable(verification).map(v -> switch (v) {
            case "at least" -> CountMatchingStrategy.GREATER_THAN_OR_EQUAL;
            case "at most" -> CountMatchingStrategy.LESS_THAN_OR_EQUAL;
            default -> CountMatchingStrategy.EQUAL_TO;
        }).orElse(CountMatchingStrategy.EQUAL_TO);
    }

    private List<ServeEvent> verifyAndGetServeEvents(CountMatchingStrategy expectedCount, RequestPatternBuilder requestPatternBuilder) {
        List<ServeEvent> serveEvents = getServeEvents(requestPatternBuilder);
        final RequestPattern requestPattern = requestPatternBuilder.build();
        int actualCount = serveEvents.size();
        if (expectedCount != null && !expectedCount.match(actualCount)) {
            throw actualCount == 0
                    ? verificationExceptionForNearMisses(requestPatternBuilder, requestPattern)
                    : new VerificationException(requestPattern, expectedCount, actualCount);
        }
        return serveEvents;
    }

    private List<ServeEvent> getServeEvents(RequestPatternBuilder requestPatternBuilder) {
        Stream<ServeEvent> stream = wireMockServer.getAllServeEvents().stream();
        return stream
                .filter(serveEvent -> requestPatternBuilder == null ||
                        RequestPattern.thatMatch(requestPatternBuilder.build()).test(serveEvent.getRequest()))
                .sorted(Comparator.comparing(serveEvent -> serveEvent.getRequest().getLoggedDate()))
                .toList();
    }

    private VerificationException verificationExceptionForNearMisses(
            RequestPatternBuilder requestPatternBuilder, RequestPattern requestPattern) {
        List<NearMiss> nearMisses = wireMockServer.findAllNearMissesFor(requestPatternBuilder);
        if (!nearMisses.isEmpty()) {
            Diff diff = new Diff(requestPattern, nearMisses.get(0).getRequest());
            return VerificationException.forUnmatchedRequestPattern(diff);
        }

        return new VerificationException(requestPattern, wireMockServer.findAll(allRequests()));
    }

    @Then(THAT + GUARD + "the interactions? on " + QUOTED_CONTENT + " (?:were|was)" + COMPARING_WITH + ":$")
    public void the_interactions_were(Guard guard, String path, Comparison comparison, Object content) {
        wiremock_has_received(guard, comparison, path, objects.resolve(content));
    }

    private void wiremock_has_received(Guard guard, Comparison comparison, String path, String expectedInteractionsStr) {
        Matcher uri = match(mocked(objects.resolve(path)));
        guard.in(objects, () -> {

            RequestPatternBuilder requestPatternBuilder = Request.builder().build().toRequestPatternBuilder(objects, uri, null, RequestMethod.ANY);
            List<ServeEvent> serveEvents = getServeEvents(requestPatternBuilder);

            List<Interaction> recordedInteractions = serveEvents.stream().map(serveEvent -> Interaction.builder().request(Request.fromLoggedRequest(serveEvent.getRequest())).response(List.of(Response.fromLoggedResponse(serveEvent.getResponse()))).build()).collect(toList());

            List<Map> parsedExpectedInteractions = Mapper.readAsAListOf(expectedInteractionsStr, Map.class);
            parsedExpectedInteractions.forEach(expectedInteraction -> expectedInteraction.computeIfPresent("response", (key, response) -> response instanceof List ? response : Collections.singletonList(response)));
            comparison.compare(recordedInteractions, Mapper.toJson(parsedExpectedInteractions));
        });
    }

    @Then(THAT + GUARD + QUOTED_CONTENT + " has received a " + SEND + " and" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void wiremock_has_received_a_call_and(Guard guard, String path, Method method, Comparison comparison, Type type, String content) {
        wiremock_has_received_a_call_and(guard, path, method, comparison, type, content, null);
    }

    private void wiremock_has_received_a_call_and(Guard guard, String path, Method method, Comparison comparison, Type type, String content, Integer count) {
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
        RequestPatternBuilder requestPatternBuilder = request.toRequestPatternBuilder(objects, uri, comparison);
        guard.in(objects, () -> assertHasReceived(requestPatternBuilder, count));
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
    public void wiremock_has_not_been_called_on(Guard guard, String path) {
        wiremock_has_received_a_call_and(guard, path, null, Comparison.CONTAINS, null, null, 0);
    }

    @Then(THAT + GUARD + "(?:the )?recorded interactions were" + COMPARING_WITH + ":$")
    public void we_received_a_request_on_paths(Guard guard, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            List<Map<String, Object>> expectedRequests = read(objects.resolve(content));
            expectedRequests.forEach(expectedRequestMap -> {
                String path = (String) expectedRequestMap.get("path");
                Matcher withoutFlagInUriMatch = match(mocked(path.replaceFirst("^\\?e ", "")));
                String uriGroup = withoutFlagInUriMatch.group(4);
                expectedRequestMap.put("path", (path.startsWith("?e ") ? "?e " : "") + uriGroup);
                expectedRequestMap.put("queryParameters", Urls.splitQuery(withoutFlagInUriMatch.group(5)));
            });

            List<ServeEvent> serveEvents = getServeEvents(null);

            List<Map<String, Object>> actualRequests = serveEvents.stream().map(serveEvent -> {
                Interaction.Request request = Request.fromLoggedRequest(serveEvent.getRequest());
                try {
                    URIBuilder uriBuilder = new URIBuilder(serveEvent.getRequest().getUrl());
                    request.path = uriBuilder.removeQuery().build().toString();
                } catch (Exception ignored) {
                }

                Map<String, Object> actualRequest = Mapper.read(Mapper.toJson(request));
                actualRequest.put("queryParameters", serveEvent.getRequest().getQueryParams());
                return actualRequest;
            }).toList();

            comparison.compare(actualRequests, expectedRequests);
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

        request = Request.builder().body(Interaction.Body.builder().type(byte[].class.getTypeName()).payload(byteArrayOutputStream.toByteArray()).build()).headers(request.headers).method(request.method).build();
        return request;
    }

    @After
    public void after() {
        if (doNotAllowUnhandledRequests) {
            List<ServeEvent> unhandledRequests = wireMockServer.getServeEvents(ServeEventQuery.ALL_UNMATCHED).getRequests();
            List<ServeEvent> forbiddenUnhandledRequests = unhandledRequests.stream().filter(serveEvent -> allowedUnhandledRequests.stream().noneMatch(allowedUnhandledRequest -> RequestPattern.thatMatch(allowedUnhandledRequest.build()).test(serveEvent.getRequest()))).toList();
            withFailMessage(() -> assertThat(forbiddenUnhandledRequests).isEmpty(), () -> "\nThere are unhandled requests:\n" +
                    forbiddenUnhandledRequests.stream().map(serveEvent -> notMatchedRenderer.render(wireMockServer, serveEvent).getBody()).collect(Collectors.joining()));
        }
    }


    public static void assertHasReceived(RequestPatternBuilder requestPatternBuilder, Integer count) {
        if (count == null) {
            wireMockServer.verify(new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN_OR_EQUAL, 1), requestPatternBuilder);
        } else {
            wireMockServer.verify(count, requestPatternBuilder);
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

    @Given(THAT + GUARD + "we don't reset mocks between tests$")
    public void we_dont_reset_mocks_between_tests(Guard guard) {
        guard.in(objects, () -> HttpSteps.resetMocksBetweenTests = false);
    }
    
}
```

## File: tzatziki-http/src/test/java/com/decathlon/tzatziki/steps/LocalSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.HttpWiremockUtils;
import com.decathlon.tzatziki.utils.Interaction;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.*;

@RequiredArgsConstructor
public class LocalSteps {
    private final HttpSteps httpSteps;
    private final ObjectSteps objects;

    static {
        Interaction.printResponses = true;
    }

    @Before
    public void before() {
        HttpSteps.resetMocksBetweenTests = true;
    }

    @Given("^we add (\\d+)-(\\d+) mocks for id endpoint$")
    public void mockIdEndpointAsSeveralMocks(int startId, int endId) {
        IntStream.range(startId, endId + 1).forEach(idx -> httpSteps.url_is_mocked_as(Guard.always(), "http://backend/" + idx, Comparison.CONTAINS, """
                request:
                    method: GET
                response:
                    headers:
                        Content-Type: application/json
                    body:
                        payload: Hello %d
                """.formatted(idx)));
    }

    @Given(THAT + "we listen for incoming request on a test-specific socket")
    public void listenPort() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        objects.add("serverSocket", serverSocket);
        new Thread(() -> {
            try {
                Socket clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                StringBuilder stringBuilder = new StringBuilder();
                int contentLength = 0;
                Pattern contentLengthPattern = Pattern.compile("Content-Length: (\\d*)");
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    stringBuilder.append(line).append("\n");
                    Matcher contentLengthMatcher = contentLengthPattern.matcher(line);
                    if (contentLengthMatcher.matches()) contentLength = Integer.parseInt(contentLengthMatcher.group(1));
                }

                char[] body = new char[contentLength];
                in.read(body, 0, contentLength);
                stringBuilder.append("\n").append(body);
                objects.add("bodyChecksum", IntStream.range(0, body.length).mapToLong(i -> (long) body[i]).sum());
                out.write("HTTP/1.1 200 OK\r\n");
                out.flush();

                in.close();
                out.close();
                clientSocket.close();
                serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    @Then(THAT + GUARD + "the received body on server socket checksum is equal to " + NUMBER)
    public void receivedBodyOnSocket(Guard guard, long checksum) {
        guard.in(objects, () -> Assertions.assertEquals((long) objects.get("bodyChecksum"), checksum));
    }

    @Given("we set relative url base path (?:to )?" + QUOTED_CONTENT + "$")
    public void calling_will_return(String relativeUrl) {
        httpSteps.setRelativeUrlRewriter(path -> HttpWiremockUtils.target(relativeUrl) + path);
    }
}
```

## File: tzatziki-http/src/test/resources/com/decathlon/tzatziki/steps/http.feature
```
Feature: to interact with an http service and setup mocks

  Background:
    Given we listen for incoming request on a test-specific socket

  Scenario Outline: we can setup a mock and call it
    Given that calling "<protocol>://backend/hello" will return:
      """yml
      message: Hello world!
      """
    When we call "<protocol>://backend/hello"
    Then we receive:
      """yml
      message: Hello world!
      """
    And "<protocol>://backend/hello" has received a GET
    When if we call "<protocol>://backend/hello"
    Then "<protocol>://backend/hello" has received exactly 2 GETs
    Then "<protocol>://backend/hello" has received at least 1 GET
    Then "<protocol>://backend/hello" has received at most 3 GETs
    Examples:
      | protocol |
      | http     |
      | https    |

  Scenario: we provide steps to assert an http response
    Given that calling "http://backend/hello" will return a status 200 and "Hello"
    Then calling "http://backend/hello" returns a status 200
    And calling "http://backend/hello" returns "Hello"
    And calling "http://backend/hello" returns:
      """
      Hello
      """
    
  Scenario: we support accent encoding
    Given that calling "http://backend/salut" will return:
      """yml
      message: Salut à tous!
      """
    When we call "http://backend/salut"
    Then we receive:
      """yml
      message: Salut à tous!
      """

  Scenario: we can assert that requests have been received in a given order
    Given that calling "http://backend/hello" will return:
      """yml
      message: Hello world!
      """
    And that posting on "http://backend/hello" will return:
      """yml
      message: Thank you!
      """
    When we call "http://backend/hello"
    And we post on "http://backend/hello":
      """yml
      message: Hello little you!
      """
    And we call "http://backend/hello"

    Then "http://backend/hello" has received in order:
      """yml
      - method: GET
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: GET
      """

    But it is not true that "http://backend/hello" has received in order:
      """yml
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: GET
      - method: GET
      """

  Scenario: we can still assert a payload as a list
    Given that posting on "http://backend/hello" will return:
      """yml
      message: Thank you!
      """
    And we post on "http://backend/hello":
      """yml
      - message: Hello little 1!
      - message: Hello little 2!
      - message: Hello little 3!
      """

    Then "http://backend/hello" has received a POST and only and in order:
      """yml
      - message: Hello little 1!
      - message: Hello little 2!
      - message: Hello little 3!
      """

  Scenario: we can assert that a mock is called with a payload
    Given that posting "http://backend/hello" will return a status OK
    When we post on "http://backend/hello":
      """yml
      message: Hello service!
      """
    Then we receive a status OK
    And "http://backend/hello" has received a POST and:
      """yml
      message: Hello service!
      """

  Scenario Template: we can assert that a mock is called with a payload conditionally
    Given that posting "http://backend/hello" will return a status <status>
    When we post on "http://backend/hello":
      """yml
      message: Hello service!
      """
    Then we receive a status <status>
    And if <status> == OK => "http://backend/hello" has received a POST and:
      """yml
      message: Hello service!
      """

    Examples:
      | status    |
      | OK        |
      | FORBIDDEN |

  Scenario: we can setup a mock with query params and call it
    Given that calling "http://backend/hello?name=bob&someParam=true" will return:
      """yml
      message: Hello bob!
      """
    When we call "http://backend/hello?name=bob&someParam=true"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario: we can access the request object to use it in the response
    Given that calling "http://backend/hello?name=.*" will return:
      """yml
      message: Hello \{{request.query.name}}! # handlebars syntax for accessing arrays
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario: we can access the request parameters with a regex to use it in the response
    Given that calling "http://backend/hello?name=(.*)" will return:
      """yml
      message: Hello $1!
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario Template: we can access the request parameters with a regex to use it in the response over a another mock
    Given that calling "http://backend/hello?provider=test&name=(.*)" will return:
      """yml
      message: Hello $1!
      """
    But that if "<name>" == "bob" => calling "http://backend/hello?provider=test&name=.*" will return a status NOT_FOUND_404
    When we call "http://backend/hello?provider=test&name=<name>"
    Then if "<name>" == "bob" => we receive a status NOT_FOUND_404
    And if "<name>" == "lisa" => we receive:
      """yml
      message: Hello <name>!
      """

    Examples:
      | name |
      | bob  |
      | lisa |

  Scenario: we can use an object to define a mock
    Given that "http://backend/hello" is mocked as:
      """yml
      request:
        method: GET
      response:
        status: OK
        headers:
          Content-Type: application/json
        delay: 10
        body:
          payload: |
            {"message":"Bonjour à tous!"}
      """
    When we call "http://backend/hello"
    Then we receive:
      """json
      {"message":"Bonjour à tous!"}
      """

  Scenario: we can explicitly allow for unhandled requests on the wiremock server (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests
    When we call "http://backend/somethingElse"
    Then we receive a status 404

  Scenario: we can explicitly allow for simple specific unhandled requests on the wiremock server (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests getting on "http://backend/somethingElse"
    When we call "http://backend/somethingElse"
    Then we receive a status 404

  Scenario: we can explicitly allow for complex specific unhandled requests on the wiremock server (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests on "http://backend/allowedUnhandled":
    """
    method: POST
    headers:
      some: ?eq header
    body.payload:
      some: ?eq payload
    """
    When we send on "http://backend/allowedUnhandled":
    """
    method: POST
    headers:
      some: header
    body.payload:
      some: payload
    """
    Then we receive a status 404

  Scenario: we can send and assert a complex request
    Given that "http://backend/something" is mocked as:
     """yml
      request:
        method: POST
        headers:
          Authorization: Bearer GeneratedToken
          Content-Type: application/xml; charset=UTF-8
        body:
          payload: |-
            <?xml version="1.0" encoding="utf-8"?>
            <something property="value"/>
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something" a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="value"/>
      """
    Then we receive a status ACCEPTED
    And "http://backend/something" has received a POST and a Request:
      """yml
      headers:
        Authorization: ?eq Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="value"/>
      """
    And "http://backend/something" has received a POST and a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      """
    But if we post on "http://backend/something" a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="some other value"/>
      """
    Then we receive a status NOT_FOUND
    * we allow unhandled mocked requests

  Scenario: we can add a pause in the mock
    Given that calling "http://backend/hello" will take 10ms to return a status OK and "Hello you!"
    Then calling "http://backend/hello" returns a status OK and "Hello you!"

  Scenario: we can override a mock
    Given that calling "http://backend/hello" will return a status 404
    But that calling "http://backend/hello" will return a status 200
    When we call "http://backend/hello"
    Then we receive a status 200

  Scenario: we can send a header in a GET request
    Given that calling "http://backend/hello" will return a status 200
    When we send on "http://backend/hello":
      """yml
      method: GET
      headers:
        Some-Token: Some-Value
      """
    Then we receive a status OK_200
    And "http://backend/hello" has received at least:
      """yml
      method: GET
      headers:
        Some-Token: Some-Value
      """

  Scenario: we can mock and assert a Response as a whole
    Given that calling "http://backend/hello" will return a Response:
      """yml
      headers:
        x-api-key: something
      body:
        payload:
          message: some value
      """
    When we call "http://backend/hello"
    Then we receive a Response:
      """yml
      headers:
        x-api-key: something
      body:
        payload:
          message: some value
      """
    And _response.headers.x-api-key == "something"
    And _response.body.payload.message == "some value"

  Scenario: we can define the assertion type in the response assert step
    Given that calling "http://backend/list" will return:
      """yml
      - id: 1
        name: thing 1
        property: test 1
      - id: 2
        name: thing 2
        property: test 2
      - id: 3
        name: thing 3
        property: null
      """
    When we call "http://backend/list"
    Then we receive at least:
      """yml
      - id: 2
      - id: 1
        name: thing 1
      """
    And we receive at least and in order:
      """yml
      - id: 1
        name: thing 1
      - id: 2
      """
    And we receive only:
      """yml
      - id: 1
      - id: 3
      - id: 2
      """
    And we receive only and in order:
      """yml
      - id: 1
      - id: 2
      - id: 3
      """
    And we receive exactly:
      """yml
      - id: 1
        name: thing 1
        property: test 1
      - id: 2
        property: test 2
        name: thing 2
      - id: 3
        name: thing 3
        property: null
      """

  Scenario: we can define the assertion type for the received payload
    Given that posting on "http://backend/users" will return a status CREATED_201
    When we post on "http://backend/users":
      """yml
      id: 1
      name: bob
      """
    And that we receive a status CREATED_201
    Then "http://backend/users" has received a POST and:
      """yml
      name: bob
      """
    And "http://backend/users" has received a POST and at least:
      """yml
      name: bob
      """
    And "http://backend/users" has received a POST and only:
      """yml
      id: 1
      """
    And "http://backend/users" has received a POST and exactly:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can template a value in the mock URL
    Given that putting on "http://backend/test/someValue" will return a status OK_200
    And that value is "someValue"
    When we put on "http://backend/test/someValue":
      """yml
      message: something
      """
    Then "http://backend/test/{{value}}" has received a PUT and:
      """yml
      message: something
      """

  Scenario: we can template a value in the caller URL
    Given that putting on "http://backend/test/someValue" will return a status OK_200
    And that value is "someValue"
    When we put on "http://backend/test/{{value}}":
      """yml
      message: something
      """
    Then "http://backend/test/someValue" has received a PUT and:
      """yml
      message: something
      """

  Scenario: overriding expectations from a previous scenario
    Given that "http://backend/test" is mocked as:
      """yml
      request:
        method: POST
      response:
        status: NOT_ACCEPTABLE
      """
    When we post on "http://backend/test" a String "plop"
    Then we receive a status NOT_ACCEPTABLE

  Scenario: we can send and assert a complex request with a json body given as a yaml
    Given that "http://backend/something" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload:
            items:
              - id: 1
              - id: 2
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something":
      """yml
      items:
        - id: 1
        - id: 2
      """
    Then we receive a status ACCEPTED

  Scenario: the order of the fields in a mock don't matter if we give a concrete type
    Given that "http://backend/something" is mocked as:
      """yml
      request:
        method: POST
        body:
          type: User
          payload:
            name: bob
            id: 1
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something":
      """yml
      id: 1
      name: bob
      """
    Then we receive a status ACCEPTED

  Scenario: a mock with a query string
    Given that calling "http://backend/test?test=1" will return "value"
    When we call "http://backend/test?test=1"
    Then we receive "value"

  Scenario: a mock with a query string that we override
    Given that calling "http://backend/test?test=1" will return "value"
    When we call "http://backend/test?test=1"
    Then we receive "value"

  Scenario Template: we can assert properly that a call has been made with headers and query params
    Given that getting on "http://backend/v1/resource?item=123&option=2" will return:
      """yml
      item_id: some-id
      """
    When we send on "http://backend/v1/resource?item=123&option=2":
      """yml
      method: GET
      headers:
        x-api-key: a-valid-api-key
        Authorization: Bearer GeneratedToken
      """
    Then "http://backend/v1/resource<params>" has received:
      """yml
      method: GET
      headers:
        x-api-key: a-valid-api-key
        Authorization: Bearer GeneratedToken
      """
    Examples:
      | params             |
      |                    |
      | .*                 |
      | ?item=12.*         |
      | ?item=123&option=2 |

  Scenario Template: we can override a mock with a lesser match between 2 scenarios
    * if <status> == ACCEPTED => calling "http://backend/test/.*/f" will return a status ACCEPTED
    * if <status> == BAD_GATEWAY => calling "http://backend/test/a/b/c/d/e/f" will return a status BAD_GATEWAY
    When we call "http://backend/test/a/b/c/d/e/f"
    Then we receive a status <status>

    Examples:
      | status      |
      | ACCEPTED    |
      | BAD_GATEWAY |
      | ACCEPTED    |

  Scenario: we can capture a path parameter and replace it with a regex
    Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
      """yml
      item_id: $1
      """
    When we call "http://backend/v1/resource/item/123"
    Then we receive:
      """yml
      item_id: 123
      """
    And "http://backend/v1/resource/item/123" has received a GET
    And "http://backend/v1/resource/item/123" has received:
      """yml
      - method: GET
      """

  Scenario: we can capture a path parameter and template it using the wiremock server request
    Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
      """yml
      item_id: \{{request.pathSegments.6}}
      """
    When we call "http://backend/v1/resource/item/123"
    Then we receive:
      """yml
      item_id: 123
      """

  Scenario: we can capture a path parameter and return a mocked list of responses
    Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
    """
    \{{#split request.pathSegments.6 ','}}
    - item_id: \{{this}}
    \{{/split}}
    """
    When we call "http://backend/v1/resource/items/1,2,3"
    Then we receive:
      """yml
      - item_id: 1
      - item_id: 2
      - item_id: 3
      """

  Scenario: we can use the body of a post to return a mocked list of responses
    Given that posting on "http://backend/v1/resource/items" will return a List:
      """hbs
      \{{#each (parseJson request.body)}}
      - id: \{{this.id}}
        name: nameOf\{{this.id}}
      \{{/each}}
      """
    When we post on "http://backend/v1/resource/items":
      """yml
      - id: 1
      - id: 2
      - id: 3
      """
    Then we receive:
      """yml
      - id: 1
        name: nameOf1
      - id: 2
        name: nameOf2
      - id: 3
        name: nameOf3
      """

  Scenario: we can make and assert a GET with a payload
    Given that getting on "http://backend/endpoint" will return:
      """yml
      message: \{{lookup (parseJson request.body) 'text'}}
      """
    When we get on "http://backend/endpoint" with:
      """yml
      text: test
      """
    Then we receive:
      """yml
      message: test
      """
    And "http://backend/endpoint" has received a GET and:
      """yml
      text: test
      """

  Scenario: we can make and assert a GET with a templated payload
    Given that getting on "http://backend/endpoint" will return:
      """yml
      message: \{{lookup (parseJson request.body) 'message.text'}}
      """
    And that payload is a Map:
      """yml
      message:
        text: test
      """
    When we get on "http://backend/endpoint" with:
      """
      {{payload}}
      """
    Then we receive:
      """yml
      message: test
      """

  Scenario: we can assert that we received a get on an url with queryParams
    Given that calling "http://backend/endpoint?param=test&user=bob" will return a status OK_200
    When we call "http://backend/endpoint?param=test&user=bob"
    And that we received a status OK_200
    Then "http://backend/endpoint?param=test&user=bob" has received a GET

  Scenario: we can assert that we received a get on an url with queryParams and a capture group
    Given that getting on "http://backend/endpoint/sub?childId=(\d+)&childType=7&type=COUNTRY_STORE" will return a status OK_200 and:
      """yml
      something: woododo
      """
    When we call "http://backend/endpoint/sub?childId=2605&childType=7&type=COUNTRY_STORE"
    And that we received a status OK_200
    Then "http://backend/endpoint/sub?childId=2605&childType=7&type=COUNTRY_STORE" has received a GET

  Scenario: we can wait to assert an interaction
    Given that getting on "http://backend/endpoint" will return a status OK
    When we get on "http://backend/endpoint"
    And that after 20ms we get "http://backend/endpoint"
    Then it is not true that during 50ms "http://backend/endpoint" has received at most 1 GET

  Scenario: we can assert a call within a timeout
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 2
          zones:
            - id: 3
      """
    Then during 10ms "http://backend/endpoint" has received at most 1 POST

  Scenario: we can assert a some complex stuff on a received payload
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 1
          zones:
            - id: 1
            - id: 2
        - id: 2
          zones:
            - id: 3
      """
    Then "http://backend/endpoint" has received a POST payload
    And payload.request.body.containers[0].zones.size == 2

  Scenario: we can assert all the posts received
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 1
          zones:
            - id: 1
            - id: 2
      """
    And we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 2
          zones:
            - id: 3
      """
    Then "http://backend/endpoint" has received 2 POST payloads
    And payloads[0].request.body.containers[0].zones.size == 2
    And payloads[1].request.body.containers[0].zones.size == 1

  Scenario: delete and NO_CONTENT
    Given that deleting on "http://backend/endpoint" will return a status NO_CONTENT_204
    When we delete on "http://backend/endpoint"
    Then we receive a status NO_CONTENT_204

  Scenario: we can assert a status and save the payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a status OK_200 and a message
    And message.key is equal to "value"

  Scenario: we can save the payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a message
    And message.key is equal to "value"

  Scenario: we can save a typed payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a Map message
    And message.size is equal to 1

  Scenario: we can assert a response in one line
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    Then a user calling "http://backend/endpoint" receives:
      """yml
      key: value
      """

  Scenario: we can assert a complex request in one line
    Given that we allow unhandled mocked requests posting on "http://backend/endpointplop"
    And that posting on "http://backend/endpointplop" will return a status NOT_FOUND_404
    And that after 100ms "http://backend/endpointplop" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload: plop
      response:
        status: ACCEPTED_202
      """
    Then within 10000ms a user sending on "http://backend/endpointplop" receives:
      """yml
      request:
        method: POST
        body:
          payload: plop
      response:
        status: ACCEPTED_202
      """

  Scenario Template: calling a url with only a subset of the repeated querystring parameters shouldn't be a match
    * we allow unhandled mocked requests
    Given that calling "http://backend/endpoint?item=1" will return a status CREATED_201
    And that calling "http://backend/endpoint?item=2" will return a status ACCEPTED_202
    And that calling "http://backend/endpoint?item=1&item=2" will return a status OK_200
    When we call "http://backend/endpoint?<params>"
    Then we receive a status <status>

    Examples:
      | params               | status        |
      | item=1               | CREATED_201   |
      | item=2               | ACCEPTED_202  |
      | item=1&item=2        | OK_200        |
      | item=2&item=1        | OK_200        |
      | item=3               | NOT_FOUND_404 |
      | item=1&item=2&item=3 | OK_200        |

  Scenario: repeated query parameters are exposed as an array in templates
    Given that calling "http://backend/collect?item=1&item=2" will return:
      """yml
      items:
        \{{#each request.query.item}}
        - \{{this}}
        \{{/each}}
      """
    When we call "http://backend/collect?item=1&item=2"
    Then we receive:
      """yml
      items:
        - 1
        - 2
      """

  Scenario: later stub overrides earlier stub for same endpoint
    Given that calling "http://backend/hello?name=(.*)" will return:
      """yml
      message: regex $1
      """
    And that calling "http://backend/hello?name=bob" will return:
      """yml
      message: literal
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: literal
      """

  Scenario: The order of items in a list should not be a matching criteria when we give in a payload of a given type (prevent exact String comparison)
    # To specify we don't want the order of an array to have an influence we can either:
    # - specify a body type different from String (JSON comparison)
    Given that "http://backend/endpoint" is mocked as:
      """yml
      request:
        method: POST
        body:
          type: List
          payload:
            - firstItem
            - secondItem
      response:
        status: OK_200
      """
    # - add a Content-Type application/json|xml
    Given that "http://backend/endpoint" is mocked as:
      """yml
      request:
        headers:
          Content-Type: application/json
        method: POST
        body:
          payload:
            - thirdItem
            - fourthItem
      response:
        status: OK_200
      """
    Then a user sending on "http://backend/endpoint" receives:
      """yml
      request:
        method: POST
        body:
          payload:
            - secondItem
            - firstItem
      response:
        status: OK_200
      """
    And a user sending on "http://backend/endpoint" receives:
      """yml
      request:
        method: POST
        body:
          payload:
            - fourthItem
            - thirdItem
      response:
        status: OK_200
      """

    Then "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - firstItem
          - secondItem
      """
    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - secondItem
          - firstItem
      """

    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - thirdItem
          - fourthItem
      """
    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - fourthItem
          - thirdItem
      """

  Scenario: We want to be able to use template for the count of request against an URI
    Given expectedNumberOfCalls is "2"
    Given that calling "http://backend/endpoint" will return a status OK_200
    When we get "http://backend/endpoint"
    And we get "http://backend/endpoint"
    Then "http://backend/endpoint" has received expectedNumberOfCalls GET

  Scenario: we can access the processing time of the last request we sent
    Given that "http://backend/hello" is mocked as:
      """yml
      request:
        method: GET
      response:
        status: OK
        delay: 10
        body:
          payload: Yo!
      """
    When we call "http://backend/hello"
    Then we receive "Yo!"
    And _response.time is equal to "?ge 10"

  Scenario: test with same bodies should not pass
    And that posting on "http://backend/hello" will return:
      """yaml
      message: Thank you!
      """
    And that we post "http://backend/hello":
      """yaml
      message: Hello little you!
      """
    And that we post "http://backend/hello":
      """yaml
      message: Hello little there!
      """

    Then it is not true that "http://backend/hello" has received only:
      """yaml
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: POST
        body:
          payload:
            message: Hello little you!
      """

  Scenario: we can assert the interactions on a mock
    Given that calling "http://backend/hello" will return a status INTERNAL_SERVER_ERROR_500
    When we call "http://backend/hello"
    Then the interaction on "http://backend/hello" was:
      """yml
      request:
        method: GET
      response:
        status: INTERNAL_SERVER_ERROR_500
      """

    But if calling "http://backend/hello" will return a status OK_200
    When we call "http://backend/hello"
    And the interactions on "http://backend/hello" were in order:
      """yml
      - response:
          status: INTERNAL_SERVER_ERROR_500
      - response:
          status: OK_200
      """

  Scenario: there shouldn't be any "within" implicit guard in HTTP response assertions
    Given that calling "http://backend/hello" will return a status NOT_FOUND_404 and:
      """
      message: API not found
      """
    Then a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: NOT_FOUND_404
        body:
          payload:
            message: API not found
      """

    And that after 500ms calling "http://backend/hello" will return a status OK_200 and:
      """
      message: hello tzatziki
      """

    Then a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: NOT_FOUND_404
      """
    And a user calling on "http://backend/hello" returns a status NOT_FOUND_404
    And a user calling on "http://backend/hello" receives a status NOT_FOUND_404 and:
      """
      message: API not found
      """
    And a user calling on "http://backend/hello" receives a Response:
      """
      status: NOT_FOUND_404
      body:
        payload:
          message: API not found
      """

    But within 600ms a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """
    And a user calling on "http://backend/hello" returns a status OK_200
    And a user calling on "http://backend/hello" receives a status OK_200 and:
      """
      message: hello tzatziki
      """
    And a user calling on "http://backend/hello" receives a Response:
      """
      status: OK_200
      body:
        payload:
          message: hello tzatziki
      """

  Scenario: there shouldn't be any "within" implicit guard in HTTP wiremock server assertions
    Given that calling "http://backend/hello" will return a status OK_200 and:
      """
      message: hello tzatziki
      """

    When a user calls "http://backend/hello"
    And after 100ms a user sends on "http://backend/hello":
      """
      method: GET
      body:
        payload:
          message: hi
      """

    Then it is not true that "http://backend/hello" has received a GET and:
      """
      message: hi
      """
    And it is not true that "http://backend/hello" has received:
      """
      method: GET
      body:
        payload:
          message: hi
      """
    And it is not true that the interactions on "http://backend/hello" were:
      """
      request:
        method: GET
        body:
          payload:
            message: hi
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """

    But within 200ms "http://backend/hello" has received a GET and:
      """
      message: hi
      """
    And "http://backend/hello" has received:
      """
      method: GET
      body:
        payload:
          message: hi
      """
    And the interactions on "http://backend/hello" were:
      """
      request:
        method: GET
        body:
          payload:
            message: hi
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """

  Scenario Template: previous test's mocks are properly deleted even if overriding mocks match them with regex
    Given that getting on "http://toto/hello/.*" will return a status 200
    Given if <idx> == 1 => getting on "http://toto/hello/1" will return a status 200
    Then getting on "http://toto/hello/1" returns a status 200

    Examples:
      | idx |
      | 1   |
      | 2   |

  Scenario: if we override an existing mock response, it should take back the priority over any in-between mocks
    Given that posting on "http://services/perform" will return a status FORBIDDEN_403
    Given that "http://services/perform" is mocked as:
      """yaml
      request:
        method: POST
        headers:
          Content-Type: application/json
        body:
          payload:
            service_id: 1
      response:
        status: INTERNAL_SERVER_ERROR_500
        headers:
          Content-Type: application/json
        body:
          payload:
            message: 'Error while performing service'
      """
    Given that posting on "http://services/perform" will return a status BAD_REQUEST_400
    Given that "http://services/perform" is mocked as:
      """yaml
      request:
        method: POST
        headers:
          Content-Type: application/json
        body:
          payload:
            service_id: 1
      response:
        status: OK_200
      """
    When we post on "http://services/perform" a Map:
      """yml
      service_id: 1
      """

    Then we received a status OK_200

  Scenario: within guard working with call_and_assert
    Given that calling on "http://backend/asyncMock" will return a status 404
    And that after 500ms calling on "http://backend/asyncMock" will return a status 200 and:
    """
      message: mocked async
    """
    Then getting on "http://backend/asyncMock" returns a status 404
    But within 10000ms getting on "http://backend/asyncMock" returns a status 200 and:
    """
      message: mocked async
    """

  Scenario Template: the "is mocked as" clause should be able to replace capture groups for json
    Given that "http://backend/hello/(.+)" is mocked as:
      """yaml
      request:
        method: GET
      response:
        status: OK_200
        body:
          payload:
            <beforeBody> hello $1<afterBody>
      """
    When we get on "http://backend/hello/toto"
    Then we received a status OK_200 and:
      """
      <beforeBody> hello toto<afterBody>
      """

    Examples:
      | beforeBody  | afterBody    |
      | message:    |              |
      | - message:  |              |
      | nothing but |              |
      | <greetings> | </greetings> |

  Scenario: Multiple calls over a capture-group-included uri should not have conflict when having concurrent calls
    Given that calling on "http://backend/hello/(.*)" will return:
      """
      hello $1
      """
    When after 50ms we get on "http://backend/hello/toto"
    And after 50ms we get on "http://backend/hello/bob"
    Then within 5000ms the interactions on "http://backend/hello/(.*)" were:
      """
      - response:
          body:
            payload: hello toto
      - response:
          body:
            payload: hello bob
      """

  Scenario: Successive calls to a mocked endpoint can reply different responses
    Given that "http://backend/time" is mocked as:
      """
      response:
        - consumptions: 1
          body:
            payload: morning
        - consumptions: 1
          body:
            payload: noon
        - consumptions: 1
          body:
            payload: afternoon
        - consumptions: 1
          body:
            payload: evening
        - status: NOT_FOUND_404
      """
    Then getting on "http://backend/time" returns "morning"
    Then getting on "http://backend/time" returns "noon"
    Then getting on "http://backend/time" returns "afternoon"
    Then getting on "http://backend/time" returns "evening"
    Then getting on "http://backend/time" returns a status 404
    Then getting on "http://backend/time" returns a status 404

  Scenario: We can use variables from request regex into response also when using an intermediary object
    Given that response is:
    """
    Hello $1
    """
    And that getting on "http://backend/hello/(.*)" will return:
    """
    {{{response}}}
    """
    When we call "http://backend/hello/toto"
    Then we received:
    """
    Hello toto
    """

  Scenario: if case doesn't match in uri, then it should return NOT_FOUND_404
    Given that we allow unhandled mocked requests
    And that getting on "http://backend/lowercase" will return a status OK_200
    When we call "http://backend/lowercase"
    Then we received a status OK_200
    But when we call "http://backend/LOWERCASE"
    Then we received a status NOT_FOUND_404

  Scenario: XML can be sent through 'we send...' step
    Given that "http://backend/xml" is mocked as:
    """
    request:
      method: POST
      body.payload: '<?xml version="1.0" encoding="utf-8"?><ns:user xmlns:ns="http://www.namespace.com">bob</ns:user>'
    response.status: OK_200
    """
    When we post on "http://backend/xml":
    """
    <?xml version="1.0" encoding="utf-8"?><ns:user xmlns:ns="http://www.namespace.com">bob</ns:user>
    """
    Then we received a status OK_200

  Scenario: Brackets should be handled and escaped properly for HTTP mocks
    Given that getting "http://invalid/regex%5B%5D?re[]toto[]=1" will return a status OK_200
    When we get "http://invalid/regex[]?re[]toto[]=1"
    Then we received a status OK_200

  Scenario Template: Exceed max amount of expectation
    Given we add 1-1 mocks for id endpoint
    Given we add <mocksRange> mocks for id endpoint
    Then getting on "http://backend/1" returns:
    """
    Hello 1
    """
    Examples:
      | mocksRange |
      | 2-150      |
      | 151-250    |

  Scenario: Interactions can also be matched with flags
    Given that posting on "http://backend/simpleApi" will return a status OK_200
    When we post on "http://backend/simpleApi" a Request:
    """
    headers:
      X-Request-ID: '12345'
    """
    And we post on "http://backend/simpleApi"
    Then the interaction on "http://backend/simpleApi" was:
    """
    request:
      method: POST
      headers:
        X-Request-ID: ?notNull
    """
    And the interaction on "http://backend/simpleApi" was only:
    """
    - request:
        method: POST
        headers:
          X-Request-ID: ?notNull
    - request:
        method: POST
        headers:
          X-Request-ID: null
    """

  Scenario Template: we support gzip compression when content-encoding header contains 'gzip'
    Given that we listen for incoming request on a test-specific socket
    When we send on "http://127.0.0.1:{{{[serverSocket.localPort]}}}":
    """yaml
    method: POST
    headers.Content-Encoding: gzip
    body:
      payload: '<rawBody>'
    """
    Then the received body on server socket checksum is equal to <gzipEncodedBodyChecksum>

    Given that we listen for incoming request on a test-specific socket
    When we send on "http://127.0.0.1:{{{[serverSocket.localPort]}}}":
    """yaml
    method: POST
    body:
      payload: '<rawBody>'
    """
    Then it is not true that the received body on server socket checksum is equal to <gzipEncodedBodyChecksum>

    Examples:
      | rawBody               | gzipEncodedBodyChecksum |
      | {"message": "hi"}     | 721742                  |
      | <message>hi</message> | 592077                  |

  @ignore @run-manually
  Scenario Template: Mocks from other tests should be considered as unhandled requests
    * a root logger set to INFO
    Given that if <idx> == 1 => getting on "http://backend/unhandled" will return a status OK_200
    And that if <idx> == 2 => getting on "http://backend/justForHostnameMock" will return a status OK_200
    Then we get on "http://backend/unhandled"

    Examples:
      | idx |
      | 1   |
      | 2   |

  @ignore @run-manually
  Scenario Template: If headers or body doesn't match against allowed unhandled requests, it should fail
    And that we allow unhandled mocked requests on "http://backend/allowedUnhandledRequest":
    """
    method: POST
    headers:
      my-header: ?eq a good value
    body:
      payload:
        my-body:
          field: ?eq a good value
    """
    When we post on "http://backend/allowedUnhandledRequest" a Request:
    """
    <request>
    """

    Examples:
      | request                                                                                         |
      | {"headers":{"my-header":"a bad value"},"body":{"payload":{"my-body":{"field":"a good value"}}}} |
      | {"headers":{"my-header":"a bad value"}}                                                         |
      | {"headers":{"my-header":"a good value"},"body":{"payload":{"my-body":{"field":"a bad value"}}}} |
      | {"body":{"payload":{"my-body":{"field":"a bad value"}}}}                                        |

  Scenario: Requests count assertion should also work for digit
    Given that getting on "http://backend/pipe/([a-z]*)/([0-9]*)/(\d+)" will return a status OK_200 and:
    """
    $1|$2|$3
    """
    When we get on "http://backend/pipe/a/1/2"
    Then we received a status OK_200 and:
    """
    a|1|2
    """
    When we get on "http://backend/pipe/c/3/4"
    Then we received a status OK_200 and:
    """
    c|3|4
    """
    And "http://backend/pipe/[a-b]*/1/\d+" has received 1 GET
    And "http://backend/pipe/.*/\d*/\d+" has received 2 GETs

  Scenario: We can assert the order in which the requests were received
    Given that getting on "http://backend/firstEndpoint" will return a status OK_200
    And that posting on "http://backend/secondEndpoint?aParam=1&anotherParam=2" will return a status OK_200
    And that patching on "http://backend/thirdEndpoint" will return a status OK_200
    When we get on "http://backend/firstEndpoint"
    And that we post on "http://backend/secondEndpoint?aParam=1&anotherParam=2" a Request:
    """
    headers.some-header: some-header-value
    body.payload.message: Hello little you!
    """
    And that we patch on "http://backend/thirdEndpoint"
    Then the recorded interactions were in order:
    """
    - method: GET
      path: http://backend/firstEndpoint
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      headers.some-header: some-header-value
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?anotherParam=2&aParam=1
      headers.some-header: ?notNull
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    But it is not true that the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?anotherParam=2&aParam=1
      headers.some-header: null
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that recorded interactions were in order:
    """
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello little you!
    - method: GET
      path: http://backend/firstEndpoint
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello BIG you!
    - method: GET
      path: http://backend/firstEndpoint
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that the recorded interactions were only:
    """
    - method: GET
      path: http://backend/firstEndpoint
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello little you!
    """

  Scenario: Http status codes are extended and not limited to WireMock ones
    Given that getting on "http://backend/tooManyRequest" will return a status TOO_MANY_REQUESTS_429
    Then getting on "http://backend/tooManyRequest" returns a status TOO_MANY_REQUESTS_429


  Scenario: Conflicting pattern are properly handled and last mock is prioritized
    Given that getting on "http://backend/test/S(\d)/path/C(\d)" will return a status TOO_MANY_REQUESTS_429

    And that getting on "http://backend/test/S1/path/C2" will return a status OK_200

    Then getting on "http://backend/test/S1/path/C2" returns a status OK_200

    And "http://backend/test/S1/path/C2" has received a GET

  Scenario: Path parameters are properly handled
    Given that getting on "http://backend/test/S(\d)/path/C(\d)" will return a status OK_200

    Then getting on "http://backend/test/S1/path/C2" returns a status OK_200
    Then getting on "http://backend/test/S2/path/C3" returns a status OK_200

    And "http://backend/test/S2/path/C3" has received a GET
    And "http://backend/test/S1/path/C2" has received a GET

  Scenario: we can use relative url
    Given we set relative url base path to "http://backend"
    Given that calling "http://backend" will return:
      """yml
      message: root path
      """
    When we call "/"
    Then we receive:
      """yml
      message: root path
      """

    Given that calling "http://backend/subpath" will return:
      """yml
      message: subpath
      """
    When we call "/subpath"
    Then we receive:
      """yml
      message: subpath
      """

  Scenario: We can use all types of equality operators when asserting headers
    Given that "http://backend/headers" is mocked as:
      """yml
      request:
        method: GET
        headers:
          exact-match: ?eq expected-value
          regex-match: ?e value-[0-9]+
          contains-match: ?contains contains-this
          not-contains-match: ?doesNotContain without-this
          greater-than: ?gt 100
          greater-equal: ?ge 100
          less-than: ?lt 100
          less-equal: ?le 100
          not-equal1: ?not unexpected-value
          not-equal2: ?ne unexpected-value
          not-equal3: ?!= unexpected-value
          in-list: ?in ['value1', 'value2', 'value3']
          not-in-list: ?notIn ['banned1', 'banned2']
          uuid-value: ?isUUID
          null-header: ?isNull
          not-null-header: ?notNull
          date-before: ?before {{@now}}
          date-after: ?after {{@now}}
        body:
          payload:
            service_id: ?gt 100
      response:
        status: OK_200
      """

    When we send on "http://backend/headers":
      """yml
      method: GET
      headers:
        exact-match: expected-value
        regex-match: value-123
        contains-match: text-contains-this-part
        not-contains-match: text-part
        greater-than: 200
        greater-equal: 100
        less-than: 50
        less-equal: 100
        not-equal1: different-value1
        not-equal2: different-value2
        not-equal3: different-value3
        in-list: value2
        not-in-list: allowed
        uuid-value: 123e4567-e89b-12d3-a456-426614174000
        not-null-header: something
        date-before: 2020-07-02T00:00:00Z
        date-after: 2050-07-02T00:00:00Z
      body:
        payload:
          service_id: 190
      """

    Then we receive a status OK_200

    And "http://backend/headers" has received a get and a Request:
      """yml
      headers:
        exact-match: ?eq expected-value
        regex-match: ?e value-[0-9]+
        contains-match: ?contains contains-this
        not-contains-match: ?doesNotContain without-this
        greater-than: ?gt 100
        greater-equal: ?ge 100
        less-than: ?lt 100
        less-equal: ?le 100
        not-equal1: ?not unexpected-value
        not-equal2: ?ne unexpected-value
        not-equal3: ?!= unexpected-value
        in-list: ?in ['value1', 'value2', 'value3']
        not-in-list: ?notIn ['banned1', 'banned2']
        uuid-value: ?isUUID
        null-header: ?isNull
        not-null-header: ?notNull
        date-before: ?before {{@now}}
        date-after: ?after {{@now}}
      body:
        payload:
          service_id: ?gt 100
      """

  Scenario: We don't use chunked transfer encoding to preserve backward compatibility with MockServer
    Given that calling "http://backend/test" will return a status OK_200
    When we get on "http://backend/test"
    Then we received a Response:
        """
        headers:
            Transfer-Encoding: ?isNull
        """

  Scenario Outline: We don't reset mock between tests if needed
    Given that we don't reset mocks between tests
    Given that "http://backend/time" is mocked as:
      """
      response:
        - consumptions: 1
          body:
            payload: id_1
        - consumptions: 1
          body:
            payload: id_2
        - consumptions: 1
          body:
            payload: id_3
      """
    Then getting on "http://backend/time" returns:
      """
      <id>
      """
    Examples:
      | id   |
      | id_1 |
      | id_2 |
      | id_3 |
```
