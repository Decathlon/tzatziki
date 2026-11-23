package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.utils.Interaction.Body;
import com.decathlon.tzatziki.utils.Interaction.Request;
import com.decathlon.tzatziki.utils.Interaction.Response;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.decathlon.tzatziki.utils.HttpUtils.*;
import static com.decathlon.tzatziki.utils.Method.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Behavioral test suite for HttpUtils.
 * Tests the public API without relying on implementation details or internal state.
 * Since HttpUtils serves as a public facade for HttpWiremockUtils, and HttpWiremockUtils is considered
 * a private implementation within Tzatziki, its features are
 * validated exclusively through this test suite.
 */
@DisplayName("HttpUtils - Behavioral Test Suite")
class HttpUtilsTest {

    private static final Function<Request, Response> UPPERCASE_TRANSFORMER = (request -> Response.builder()
            .body(Body.builder().payload(request.body.payload.toString().toUpperCase()).build())
            .status("200")
            .build());

    @AfterEach
    void cleanup() {
        reset();
    }

    @Nested
    @DisplayName("URL Generation & Configuration")
    class UrlGeneration {

        @Test
        @DisplayName("should provide valid base URL with localhost and valid port")
        void providesValidBaseUrl() {
            assertThat(url()).isEqualTo("http://localhost:" + localPort());
        }

        @Test
        @DisplayName("should provide port number in valid range")
        void providesValidPortNumber() {
            assertThat(localPort())
                    .isNotNull()
                    .isBetween(1024, 65535);
        }

        @Test
        @DisplayName("should return URLs")
        void preservesQueryParametersInUnmockedPaths() {
            assertThat(target("/api/users?page=1&size=10")).isEqualTo("/api/users?page=1&size=10");
        }

        @Test
        @DisplayName("should handle empty path")
        void handlesEmptyPath() {
            assertThat(target("")).isEmpty();
        }

        @Test
        @DisplayName("should handle root path")
        void handlesRootPath() {
            assertThat(target("/")).isEqualTo("/");
        }

        @Test
        @DisplayName("should handle URLs in protocol://host:port/path form")
        void handleHosts() {
            assertThat(target("http://example.com/path")).isEqualTo("http://example.com/path");
            assertThat(target("http://example.com:8080/path")).isEqualTo("http://example.com:8080/path");
            assertThat(target("https://example.com:8080/path")).isEqualTo("https://example.com:8080/path");
        }
    }

    @Nested
    @DisplayName("URL remapping")
    class UrlRemapping {
        @Test
        void noRemapWhenNoHost() {
            String mocked = HttpWiremockUtils.mocked("/api/path");
            assertThat(mocked).isEqualTo("/api/path");
            assertThat(target("/api/path")).isEqualTo("/api/path");
        }

        @Test
        void remapAsMocked() {
            String mocked = HttpWiremockUtils.mocked("http://example.com/api/path");
            assertThat(mocked).isEqualTo("/_mocked/http/example.com/api/path");
            assertThat(target("http://example.com/api/path")).isEqualTo(url() + "/_mocked/http/example.com/api/path");
        }

        @Test
        void doNotRemapWhenNotMocked() {
            assertThat(target("http://example.com/api/path")).isEqualTo("http://example.com/api/path");
        }
    }

    @Nested
    @DisplayName("Simple Request Mocking")
    class SimpleRequestMocking {

        @Test
        @DisplayName("should mock GET request and return expected response")
        void mocksGetRequest() {
            String path = "/api/health";
            String responseBody = "{\"status\":\"ok\"}";

            mockSimpleRequest(path, GET, "200", responseBody);

            String response = given()
                    .baseUri(url())
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200)
                    .contentType("application/json")
                    .extract().body().asString();

            assertThat(response).isEqualTo(responseBody);
        }

        @Test
        @DisplayName("should mock POST request with body")
        void mocksPostRequestWithBody() {
            String path = "/api/users";
            String responseBody = "{\"id\":\"123\",\"name\":\"John\"}";

            mockSimpleRequest(path, POST, "201", responseBody);

            given()
                    .baseUri(url())
                    .contentType("application/json")
                    .body("{\"name\":\"John\"}")
                    .when()
                    .post(path)
                    .then()
                    .statusCode(201)
                    .body(equalTo(responseBody));
        }

        @Test
        @DisplayName("should mock request without response body")
        void mocksRequestWithoutBody() {
            String path = "/api/ping";

            mockSimpleRequest(path, GET, "200", null);

            given()
                    .baseUri(url())
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("should handle paths with query parameters")
        void handlesPathsWithQueryParameters() {
            String path = "/api/search?q=test";
            String responseBody = "{\"results\":[]}";

            mockSimpleRequest(path, GET, "200", responseBody);

            given()
                    .baseUri(url())
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200);
        }
    }

    @Nested
    @DisplayName("Full Interaction Mocking")
    class InteractionMocking {

        @Test
        @DisplayName("should mock interaction with custom headers")
        void mocksInteractionWithHeaders() {
            Interaction interaction = Interaction.builder()
                    .request(Request.builder()
                            .path("/api/secure")
                            .method(GET)
                            .headers(Map.of("Authorization", "Bearer token123"))
                            .build())
                    .response(List.of(Response.builder()
                            .status("200")
                            .body(Body.builder()
                                    .payload("{\"secured\":true}")
                                    .build())
                            .build()))
                    .build();

            mockInteraction(interaction, Comparison.CONTAINS, null);

            given()
                    .baseUri(url())
                    .header("Authorization", "Bearer token123")
                    .when()
                    .get("/api/secure")
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("should mock interaction with request body")
        void mocksInteractionWithRequestBody() {
            String requestPayload = "{\"username\":\"test\"}";
            String responsePayload = "{\"token\":\"abc123\"}";

            Interaction interaction = Interaction.builder()
                    .request(Request.builder()
                            .path("/api/login")
                            .method(POST)
                            .body(Body.builder()
                                    .payload(requestPayload)
                                    .build())
                            .build())
                    .response(List.of(Response.builder()
                            .status("200")
                            .body(Body.builder()
                                    .payload(responsePayload)
                                    .build())
                            .build()))
                    .build();

            mockInteraction(interaction, Comparison.CONTAINS, null);

            given()
                    .baseUri(url())
                    .contentType("application/json")
                    .body(requestPayload)
                    .when()
                    .post("/api/login")
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("should mock interaction using comparison")
        void mocksInteractionWithComparison() {
            Interaction interactionWithContains = Interaction.builder()
                    .request(Request.builder()
                            .path("/api/contains")
                            .method(POST)
                            .body(Body.builder().payload("foo").build())
                            .build())
                    .build();

            Interaction interactionWithExact = Interaction.builder()
                    .request(Request.builder()
                            .path("/api/exact")
                            .method(POST)
                            .body(Body.builder().payload("foo").build())
                            .build())
                    .build();

            mockInteraction(interactionWithContains, Comparison.CONTAINS, null);
            mockInteraction(interactionWithExact, Comparison.EQUALS, null);

            given()
                    .baseUri(url())
                    .when()
                    .body("[\"foo\",\"bar\"]") // This contains 'foo' as expected
                    .post("/api/contains")
                    .then()
                    .statusCode(200);
            given()
                    .baseUri(url())
                    .when()
                    .body("[\"bar\"]") // This does not contains 'foo'
                    .post("/api/contains")
                    .then()
                    .statusCode(404)
                    .body(Matchers.containsString("Request was not matched"));
            given()
                    .baseUri(url())
                    .when()
                    .body("foo")
                    .post("/api/exact")
                    .then()
                    .statusCode(200);
            given()
                    .baseUri(url())
                    .when()
                    .body("[\"foo\",\"bar\"]") // This body contains 'foo' but does not match 'foo' exactly
                    .post("/api/exact")
                    .then()
                    .statusCode(404)
                    .body(Matchers.containsString("Request was not matched"));
        }

        @Test
        @DisplayName("should mock interaction with multiple response fields")
        void mocksInteractionWithComplexResponse() {
            Interaction interaction = Interaction.builder()
                    .request(Request.builder()
                            .path("/api/data")
                            .method(GET)
                            .build())
                    .response(List.of(Response.builder()
                            .status("200")
                            .headers(Map.of(
                                    "Content-Type", "application/json",
                                    "X-Custom-Header", "custom-value"
                            ))
                            .body(Body.builder()
                                    .payload("{\"data\":\"value\"}")
                                    .build())
                            .build()))
                    .build();

            mockInteraction(interaction, Comparison.CONTAINS, null);

            given()
                    .baseUri(url())
                    .when()
                    .get("/api/data")
                    .then()
                    .statusCode(200)
                    .header("Content-Type", "application/json")
                    .header("X-Custom-Header", "custom-value");
        }

        @Test
        @DisplayName("should return a custom response")
        void mocksWithCustomResponse() {
            Interaction interaction = Interaction.builder()
                    .request(Request.builder()
                            .path("/api/post")
                            .method(POST)
                            .build())
                    .response(List.of(Response.builder()
                            .status("200")
                            .body(Body.builder()
                                    .payload("ignored response payload")
                                    .build())
                            .build()))
                    .build();

            mockInteraction(interaction, Comparison.CONTAINS, UPPERCASE_TRANSFORMER);

            given()
                    .baseUri(url())
                    .body("request payload")
                    .when()
                    .post("/api/post")
                    .then()
                    .statusCode(200)
                    .body(equalTo("REQUEST PAYLOAD"));

        }
    }

    @Nested
    @DisplayName("Request Verification")
    class RequestVerification {

        @Test
        @DisplayName("should verify request was received once")
        void verifiesRequestReceivedOnce() {
            String path = "/api/verify";
            mockSimpleRequest(path, GET, "200", null);

            given()
                    .baseUri(url())
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200);

            Request request = Request.builder()
                    .path(path)
                    .method(GET)
                    .build();

            assertThatCode(() -> verify(request, 1))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should verify request was received multiple times")
        void verifiesRequestReceivedMultipleTimes() {
            String path = "/api/multiple";
            mockSimpleRequest(path, GET, "200", null);

            // Make 3 requests
            for (int i = 0; i < 3; i++) {
                given()
                        .baseUri(url())
                        .when()
                        .get(path)
                        .then()
                        .statusCode(200);
            }

            Request request = Request.builder()
                    .path(path)
                    .method(GET)
                    .build();

            assertThatCode(() -> verify(request, 3))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should verify request with specific headers")
        void verifiesRequestWithHeaders() {
            String path = "/api/headers";
            mockSimpleRequest(path, POST, "200", null);

            given()
                    .baseUri(url())
                    .header("X-Custom", "value123")
                    .contentType("application/json")
                    .body("{}")
                    .when()
                    .post(path)
                    .then()
                    .statusCode(200);

            Request request = Request.builder()
                    .path(path)
                    .method(POST)
                    .headers(Map.of("X-Custom", "value123"))
                    .build();

            assertThatCode(() -> verify(request, 1))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should verify POST request with body")
        void verifiesPostRequestWithBody() {
            String path = "/api/post";
            String requestBody = "{\"test\":\"data\"}";
            mockSimpleRequest(path, POST, "201", null);

            given()
                    .baseUri(url())
                    .contentType("application/json")
                    .body(requestBody)
                    .when()
                    .post(path)
                    .then()
                    .statusCode(201);

            Request request = Request.builder()
                    .path(path)
                    .method(POST)
                    .body(Body.builder()
                            .payload(requestBody)
                            .build())
                    .build();

            assertThatCode(() -> verify(request, 1))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Lifecycle Management")
    class LifecycleManagement {

        @Test
        @DisplayName("should reset and clear all mocks")
        void resetsAllMocks() {
            String path = "/api/temp";
            mockSimpleRequest(path, GET, "200", "{\"temp\":true}");

            // Verify mock works
            given()
                    .baseUri(url())
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200);

            // Reset
            reset();

            // Verify mock no longer exists
            given()
                    .baseUri(url())
                    .when()
                    .get(path)
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("should allow re-mocking after reset")
        void allowsRemockingAfterReset() {
            String path = "/api/remock";
            mockSimpleRequest(path, GET, "200", "{\"version\":1}");

            reset();

            mockSimpleRequest(path, GET, "200", "{\"version\":2}");

            String response = given()
                    .baseUri(url())
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200)
                    .extract().body().asString();

            assertThat(response).contains("\"version\":2");
        }

        @Test
        @DisplayName("should reset verification state")
        void resetsVerificationState() {
            String path = "/api/verify-reset";
            mockSimpleRequest(path, GET, "200", null);

            // Make request
            given()
                    .baseUri(url())
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200);

            // Reset
            reset();

            // Re-mock
            mockSimpleRequest(path, GET, "200", null);

            // Verify count is reset to 0 (request should fail if we verify for 1)
            Request request = Request.builder()
                    .path(path)
                    .method(GET)
                    .build();

            // After reset, no requests should have been received yet
            assertThatThrownBy(() -> verify(request, 1))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("should preserve persistent mocks across resets")
        void persistentMocksPersistAcrossResets() {

            // Set up a mock that should persist even after calling reset():
            persistentMockInteraction(Interaction.builder()
                            .request(Request.builder()
                                    .path("/persistent-mock")
                                    .headers(Map.of("x-custom-request-header", "foo"))
                                    .body(Body.builder().payload("request payload").build())
                                    .method(POST)
                                    .build())
                            .response(List.of(Response.builder()
                                    .headers(Map.of("x-custom-response-header", "bar"))
                                    .body(Body.builder().payload("persistent mock response payload").build())
                                    .status("200")
                                    .build()))
                            .build(),
                    Comparison.CONTAINS,
                    null); // Persists across resets

            // Set up a classic mock that should not persist across resets:
            mockSimpleRequest("/temp", GET, "200", "response payload");

            // Verify non-persistent mock works:
            given()
                    .baseUri(url())
                    .when()
                    .get("/temp")
                    .then()
                    .statusCode(200)
                    .body(equalTo("response payload"));

            // Verify persistent mock works:
            given()
                    .baseUri(url())
                    .when()
                    .headers(Map.of("x-custom-request-header", "foo"))
                    .body("request payload")
                    .post("/persistent-mock")
                    .then()
                    .statusCode(200)
                    .headers(Map.of("x-custom-response-header", "bar"))
                    .body(equalTo("persistent mock response payload"));

            reset();

            // Verify non-persistent mock no longer exists:
            given()
                    .baseUri(url())
                    .when()
                    .get("/temp")
                    .then()
                    .statusCode(404);

            // Verify persistent mock survived the reset:
            given()
                    .baseUri(url())
                    .when()
                    .headers(Map.of("x-custom-request-header", "foo"))
                    .body("request payload")
                    .post("/persistent-mock")
                    .then()
                    .statusCode(200)
                    .headers(Map.of("x-custom-response-header", "bar"))
                    .body(equalTo("persistent mock response payload"));
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("should handle complete REST workflow")
        void handlesCompleteRestWorkflow() {
            String basePath = "/api/items";

            // Create
            mockSimpleRequest(basePath, POST, "201", "{\"id\":\"1\"}");
            String createResponse = given()
                    .baseUri(url())
                    .contentType("application/json")
                    .body("{\"name\":\"Item 1\"}")
                    .when()
                    .post(basePath)
                    .then()
                    .statusCode(201)
                    .extract().body().asString();

            assertThat(createResponse).contains("\"id\":\"1\"");

            // Read
            mockSimpleRequest(basePath + "/1", GET, "200", "{\"id\":\"1\",\"name\":\"Item 1\"}");
            String readResponse = given()
                    .baseUri(url())
                    .when()
                    .get(basePath + "/1")
                    .then()
                    .statusCode(200)
                    .extract().body().asString();

            assertThat(readResponse)
                    .contains("\"id\":\"1\"")
                    .contains("\"name\":\"Item 1\"");

            // Update
            mockSimpleRequest(basePath + "/1", PUT, "200", "{\"id\":\"1\",\"name\":\"Updated Item\"}");
            given()
                    .baseUri(url())
                    .contentType("application/json")
                    .body("{\"name\":\"Updated Item\"}")
                    .when()
                    .put(basePath + "/1")
                    .then()
                    .statusCode(200);

            // Delete
            mockSimpleRequest(basePath + "/1", DELETE, "204", null);
            given()
                    .baseUri(url())
                    .when()
                    .delete(basePath + "/1")
                    .then()
                    .statusCode(204);
        }

        @Test
        @DisplayName("should handle multiple concurrent mocks")
        void handlesMultipleConcurrentMocks() {
            mockSimpleRequest("/api/endpoint1", GET, "200", "{\"source\":\"endpoint1\"}");
            mockSimpleRequest("/api/endpoint2", GET, "200", "{\"source\":\"endpoint2\"}");
            mockSimpleRequest("/api/endpoint3", POST, "201", "{\"source\":\"endpoint3\"}");

            given().baseUri(url()).when().get("/api/endpoint1")
                    .then().statusCode(200).body(containsString("endpoint1"));
            given().baseUri(url()).when().get("/api/endpoint2")
                    .then().statusCode(200).body(containsString("endpoint2"));
            given().baseUri(url()).contentType("application/json").body("{}")
                    .when().post("/api/endpoint3")
                    .then().statusCode(201).body(containsString("endpoint3"));
        }

        @Test
        @DisplayName("should handle URL generation and mocking together")
        void handlesUrlGenerationAndMocking() {
            String path = "/api/combined";
            mockSimpleRequest(path, GET, "200", "{\"message\":\"success\"}");

            // Build full URL using url() method
            String fullUrl = url() + path;

            RestAssured
                    .given()
                    .when()
                    .get(fullUrl)
                    .then()
                    .statusCode(200)
                    .body(containsString("success"));
        }
    }

    @Nested
    @DisplayName("OAuth2 Client Credentials Flow")
    class OAuth2ClientCredentialsFlow {

        @AfterEach
        void cleanupOAuth2() {
            OAuth2ClientCredentialsStore.reset();
        }

        @Test
        @DisplayName("should throw when fetching token for unregistered client")
        void throwsForUnregisteredClient() {
            assertThatThrownBy(() -> OAuth2ClientCredentialsStore.getAccessToken("unknown-client"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("No OAuth2 access token found for clientId: unknown-client");
        }

        @Test
        @DisplayName("should correctly identify registered clients")
        void identifiesRegisteredClients() {
            // Register URL as mocked and get the mocked path
            String tokenUrl = "http://auth-server/oauth/token";
            String mockedPath = HttpWiremockUtils.mocked(tokenUrl);
            mockSimpleRequest(mockedPath, POST, "200",
                    "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\"}");

            // Register client
            OAuth2ClientCredentialsStore.registerClient("registered-client", "secret", tokenUrl);

            assertThat(OAuth2ClientCredentialsStore.hasClient("registered-client")).isTrue();
            assertThat(OAuth2ClientCredentialsStore.hasClient("unregistered-client")).isFalse();
        }

        @Test
        @DisplayName("should reset clears all cached tokens and configurations")
        void resetClearsAllData() {
            // Register URL as mocked and get the mocked path
            String tokenUrl = "http://auth-server/oauth/token";
            String mockedPath = HttpWiremockUtils.mocked(tokenUrl);
            mockSimpleRequest(mockedPath, POST, "200",
                    "{\"access_token\":\"token-to-clear\",\"token_type\":\"Bearer\"}");

            // Register client
            OAuth2ClientCredentialsStore.registerClient("client-to-reset", "secret", tokenUrl);
            assertThat(OAuth2ClientCredentialsStore.hasClient("client-to-reset")).isTrue();

            // Reset
            OAuth2ClientCredentialsStore.reset();

            // Verify client is no longer registered
            assertThat(OAuth2ClientCredentialsStore.hasClient("client-to-reset")).isFalse();
        }

        @Test
        @DisplayName("should throw when token endpoint returns error status")
        void throwsOnTokenEndpointError() {
            // Register URL as mocked and get the mocked path
            String tokenUrl = "http://auth-server-error/oauth/token";
            String mockedPath = HttpWiremockUtils.mocked(tokenUrl);
            mockSimpleRequest(mockedPath, POST, "401",
                    "{\"error\":\"invalid_client\",\"error_description\":\"Client authentication failed\"}");

            assertThatThrownBy(() -> OAuth2ClientCredentialsStore.registerClient(
                    "invalid-client", "wrong-secret", tokenUrl))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("OAuth2 token request failed");
        }

        @Test
        @DisplayName("should throw when token response does not contain access_token")
        void throwsOnMissingAccessToken() {
            // Register URL as mocked and get the mocked path
            String tokenUrl = "http://auth-server-missing/oauth/token";
            String mockedPath = HttpWiremockUtils.mocked(tokenUrl);
            mockSimpleRequest(mockedPath, POST, "200",
                    "{\"token_type\":\"Bearer\",\"expires_in\":3600}");

            assertThatThrownBy(() -> OAuth2ClientCredentialsStore.registerClient(
                    "test-client", "test-secret", tokenUrl))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("does not contain 'access_token'");
        }
    }
}
