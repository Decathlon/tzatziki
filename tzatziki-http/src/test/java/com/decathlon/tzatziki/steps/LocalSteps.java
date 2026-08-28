package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.configuration.HttpConfigurationProperties;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.HttpWiremockUtils;
import com.decathlon.tzatziki.utils.Interaction;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Assertions;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.decathlon.tzatziki.steps.HttpSteps.wireMockServer;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.HttpWiremockUtils.mocked;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;

@RequiredArgsConstructor
public class LocalSteps {
    private static final byte[] TEST_OCTET_STREAM_BYTES = Base64.getDecoder().decode("AH+A/0HDKAo=");
    private static final String API_OCTET_STREAM = "http://backend/api/octet-stream";

    private final HttpSteps httpSteps;
    private final ObjectSteps objects;

    static {
        Interaction.printResponses(true);
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

    /**
     * Sets the OAuth2 token URL via system property for testing purposes.
     * This allows tests to configure the token URL fallback without specifying it in the docstring.
     *
     * @param guard    the guard for conditional execution
     * @param tokenUrl the OAuth2 token endpoint URL
     */
    @Given(THAT + GUARD + "the oauth2 token url is " + QUOTED_CONTENT + "$")
    public void set_oauth2_token_url(Guard guard, String tokenUrl) {
        guard.in(objects, () -> {
            String resolvedTokenUrl = objects.resolve(tokenUrl);
            System.setProperty(HttpConfigurationProperties.OAUTH2_TOKEN_URL, resolvedTokenUrl);
        });
    }

    /**
     * Clear the OAuth2 token URL system property for testing purposes.
     *
     * @param guard    the guard for conditional execution
     */
    @Given(THAT + GUARD + "the global oauth2 token url is cleared")
    public void clear_oauth2_token_url(Guard guard) {
        guard.in(objects, () -> {
            System.clearProperty(HttpConfigurationProperties.OAUTH2_TOKEN_URL);
        });
    }

    @Given("^we mock a test octet-stream$")
    public void mock_octet_stream() {
        wireMockServer.stubFor(get(urlEqualTo(mocked(API_OCTET_STREAM)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                        .withBody(TEST_OCTET_STREAM_BYTES)));
    }

    @Then("^we call and assert the octet-stream is valid$")
    public void assert_octet_stream() {
        String target = HttpWiremockUtils.target(API_OCTET_STREAM);
        byte[] actual = given()
                .get(target)
                .asByteArray();

        Assertions.assertArrayEquals(TEST_OCTET_STREAM_BYTES, actual,
                "The octet-stream received from the HTTP response does not match the mocked binary body");
    }
}
