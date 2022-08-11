package com.decathlon.tzatziki.utils;

import io.restassured.response.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockserver.model.JsonBody;

import java.util.List;

import static com.decathlon.tzatziki.utils.Matchers.equalsInOrder;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

class MockFasterTest {

    @Test
    void defaultUseCase() {
        MockFaster.when(request().withMethod("GET").withPath("/test"), Comparison.CONTAINS)
                .respond(response()
                        .withContentType(APPLICATION_JSON)
                        .withBody("""
                                {
                                    "message": "wabadabadaboo"
                                }
                                """)
                );

        when().get(MockFaster.url() + "/test").then()
                .assertThat()
                .body(equalsInOrder("message: wabadabadaboo"));

        MockFaster.assertHasReceivedAtLeast(request().withMethod("GET").withPath("/test"));
        MockFaster.assertHasReceivedAtLeast(List.of(request().withMethod("GET").withPath("/test")));
    }

    @Test
    void jsonMatcherWithArraysTest() {
        final JsonBody defaultComparisonBody = new JsonBody("{\"message\": [\"wabadabadaboo\"]}");
        MockFaster.when(request().withMethod("POST").withPath("/test").withBody(defaultComparisonBody), Comparison.CONTAINS)
                .respond(response()
                        .withContentType(APPLICATION_JSON)
                        .withBody("""
                                {
                                    "message": "matcher with extra elements allowed without array order"
                                }
                                """)
                );

        final JsonBody intermediateStrictnessComparisonBody = new JsonBody("{\"message\": [\"wabadabadaboo\", \"hello\", \"toto\"]}");
        MockFaster.when(request().withMethod("POST").withPath("/test").withBody(intermediateStrictnessComparisonBody), Comparison.CONTAINS_ONLY)
                .respond(response()
                        .withContentType(APPLICATION_JSON)
                        .withBody("""
                                {
                                    "message": "matcher without array order but extra elements not allowed"
                                }
                                """)
                );

        final JsonBody strictComparisonBody = new JsonBody("{\"message\": [\"hello\", \"wabadabadaboo\", \"toto\"]}");
        MockFaster.when(request().withMethod("POST").withPath("/test").withBody(strictComparisonBody), Comparison.IS_EXACTLY)
                .respond(response()
                        .withContentType(APPLICATION_JSON)
                        .withBody("""
                                {
                                    "message": "matcher with array order and extra elements not allowed"
                                }
                                """)
                );

        with().contentType("application/json").body("{\"message\": [\"wabadabadaboo\", \"anExtraElement\"]}").when().post(MockFaster.url() + "/test").then()
                .assertThat()
                .body(equalsInOrder("message: matcher with extra elements allowed without array order"));

        with().contentType("application/json").body("{\"message\": [\"wabadabadaboo\", \"toto\", \"hello\"]}").when().post(MockFaster.url() + "/test").then()
                .assertThat()
                .body(equalsInOrder("message: matcher without array order but extra elements not allowed"));

        with().contentType("application/json").body("{\"message\": [\"hello\", \"wabadabadaboo\", \"toto\"]}").when().post(MockFaster.url() + "/test").then()
                .assertThat()
                .body(equalsInOrder("message: matcher with array order and extra elements not allowed"));
    }

    @Test
    void urlShouldBeCaseSensitive() {
        MockFaster.add_mock(request()
                .withMethod("GET")
                .withPathSchema("""
                        {
                          "type": "string",
                          "pattern": "/lowercase/(\\\\d+)"
                        }
                        """),
                request -> response(),
                Comparison.CONTAINS);

        Response response = when().get(MockFaster.url() + "/lowercase/1");
        when().get(MockFaster.url() + "/LOWERCASE/1").then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    @Disabled // to run manually on mac
    void manyCalls() {
        for (int i = 0; i < 1000; i++) {
            if (i % 100 == 0) {
                System.out.printf("created %d mocks%n", i);
            }
            MockFaster.when(request().withMethod("GET").withPath("/test/" + i), Comparison.CONTAINS)
                    .respond(response()
                            .withContentType(APPLICATION_JSON)
                            .withBody("message: wabadabadaboo")
                    );

            when().get(MockFaster.url() + "/test/" + i).then()
                    .assertThat()
                    .body(equalsInOrder("message: wabadabadaboo"));
            MockFaster.reset();
        }
    }
}
