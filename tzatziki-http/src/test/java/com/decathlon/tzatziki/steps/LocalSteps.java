package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.HttpWiremockUtils.target;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static io.restassured.RestAssured.given;

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

    @Then("getting (?:on )?" + QUOTED_CONTENT + " four times in parallel returns:$")
    public void sendInParallel(String path, String content) {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        List<CompletableFuture<Interaction.Response>> responsesAsync = List.of(
                CompletableFuture.supplyAsync(() -> Interaction.Response.fromResponse(Interaction.Request.builder().build().send(given(), target(path), objects)), executor),
                CompletableFuture.supplyAsync(() -> Interaction.Response.fromResponse(Interaction.Request.builder().build().send(given(), target(path), objects)), executor),
                CompletableFuture.supplyAsync(() -> Interaction.Response.fromResponse(Interaction.Request.builder().build().send(given(), target(path), objects)), executor),
                CompletableFuture.supplyAsync(() -> Interaction.Response.fromResponse(Interaction.Request.builder().build().send(given(), target(path), objects)), executor)
        );

        Asserts.contains(responsesAsync.stream().map(future -> unchecked(() -> future.get())).map(response -> response.body.payload).toList(), objects.resolve(content));
    }

    @Given("we set relative url base path (?:to )?" + QUOTED_CONTENT + "$")
    public void calling_will_return(String relativeUrl) {
        httpSteps.setRelativeUrlRewriter(path -> HttpWiremockUtils.target(relativeUrl) + path);
    }

    @Given(THAT + GUARD + "we don't reset mocks between tests$")
    public void we_dont_reset_mocks_between_tests(Guard guard) {
        guard.in(objects, () -> HttpSteps.resetMocksBetweenTests = false);
    }
}
