package com.decathlon.tzatziki.utils;

import org.junit.jupiter.api.Test;

import static com.decathlon.tzatziki.utils.Matchers.equalsInOrder;
import static io.restassured.RestAssured.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

class MockFasterTest {

    @Test
    public void test() {
        MockFaster.when(request().withMethod("GET").withPath("/test"))
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
    }
}
