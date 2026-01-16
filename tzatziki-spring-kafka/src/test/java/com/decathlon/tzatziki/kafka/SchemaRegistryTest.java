package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.steps.HttpSteps;
import com.decathlon.tzatziki.utils.HttpUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class SchemaRegistryTest {

    private RestClient restClient;
    private String schemaRegistryEndpoint;

    @BeforeEach
    void setUp() {
        schemaRegistryEndpoint = "http://localhost:" + HttpSteps.localPort + "/schemaRegistry/";
        SchemaRegistry.initialize();
        restClient = RestClient.create();
    }
    
    @AfterEach
    void reset() {
        HttpUtils.reset();
    }

    @Test
    void registerAndRetrieveSchema() throws JSONException {
        // POST schema registration
        String postResponse = postSchemaRegistration("test-subject", "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}");
        int id = new JSONObject(postResponse).getInt("id");

        // GET schema by ID
        String getResponse = getSchemaById(id);
        assertThat(getResponse).isNotNull();
        String schema = "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"field1\"," +
                "\"type\":\"string\"}]}";
        assertThat(new JSONObject(getResponse).getString("schema")).isEqualTo(schema);
    }
    
    // Test for issue https://github.com/Decathlon/tzatziki/issues/645
    @Test
    void registerMoreThan10Schemas() throws JSONException {
        for (int i = 1; i <= 12; i++) {
            String subject = "test-subject-" + i;
            String schema1 = String.format(
                    "{\"type\":\"record\",\"name\":\"TestRecord%d\",\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}",
                    i);
            String postResponse = postSchemaRegistration(subject, schema1);
            int id = new JSONObject(postResponse).getInt("id");

            String getResponse = getSchemaById(id);
            assertThat(new JSONObject(getResponse).getString("schema")).isEqualTo(schema1);
        }
    }

    @Test
    void mocksPersistAcrossWiremockResets() throws JSONException {
        // POST schema registration
        String postResponse = postSchemaRegistration("test-subject", "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}");
        int id = new JSONObject(postResponse).getInt("id");

        HttpUtils.reset();
        
        // GET schema by ID
        String getResponse = getSchemaById(id);
        assertThat(getResponse).isNotNull();
        String schema = "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"field1\"," +
                "\"type\":\"string\"}]}";
        assertThat(new JSONObject(getResponse).getString("schema")).isEqualTo(schema);
    }
    
    private String postSchemaRegistration(String subject, String schema) throws JSONException {
        String registerUrl = schemaRegistryEndpoint + "subjects/" + subject + "/versions";
        JSONObject payload = new JSONObject().put("schema", schema);

        return restClient.post()
                .uri(registerUrl)
                .contentType(APPLICATION_JSON)
                .body(payload.toString())
                .retrieve()
                .body(String.class);
    }

    private String getSchemaById(int id) {
        return restClient.get()
                .uri(schemaRegistryEndpoint + "schemas/ids/" + id + "?someQueryParam")
                .retrieve()
                .body(String.class);
    }
}