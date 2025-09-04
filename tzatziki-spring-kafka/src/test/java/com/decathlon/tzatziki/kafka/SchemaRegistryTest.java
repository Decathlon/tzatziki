package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.steps.HttpSteps;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class SchemaRegistryTest {

    private RestTemplate restTemplate;
    private String schemaRegistryEndpoint;

    @BeforeEach
    void setUp() {
        schemaRegistryEndpoint = "http://localhost:" + HttpSteps.localPort + "/schemaRegistry/";
        SchemaRegistry.initialize();
        restTemplate = new RestTemplate();
    }

    @SneakyThrows
    @Test
    void registerAndRetrieveSchema() {
        // POST schema registration
        ResponseEntity<String> postResponse = postSchemaRegistration("test-subject", "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}");
        assertThat(postResponse.getStatusCode()).isEqualTo(OK);
        int id = new JSONObject(postResponse.getBody()).getInt("id");

        // GET schema by ID
        ResponseEntity<String> getResponse = getSchemaById(id);
        assertThat(getResponse.getStatusCode()).isEqualTo(OK);
        String schema = "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"field1\"," +
                "\"type\":\"string\"}]}";
        assertThat(new JSONObject(getResponse.getBody()).getString("schema")).isEqualTo(schema);
    }
    
    // Test for issue https://github.com/Decathlon/tzatziki/issues/645
    @SneakyThrows
    @Test
    void registerMoreThan10Schemas() {
        for (int i = 1; i <= 12; i++) {
            String subject = "test-subject-" + i;
            String schema1 = String.format(
                    "{\"type\":\"record\",\"name\":\"TestRecord%d\",\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}",
                    i);
            ResponseEntity<String> postResponse = postSchemaRegistration(subject, schema1);
            assertThat(postResponse.getStatusCode()).isEqualTo(OK);
            int id = new JSONObject(postResponse.getBody()).getInt("id");

            ResponseEntity<String> getResponse = getSchemaById(id);
            assertThat(getResponse.getStatusCode()).isEqualTo(OK);
            assertThat(new JSONObject(getResponse.getBody()).getString("schema")).isEqualTo(schema1);
        }
    }

    private @NotNull ResponseEntity<String> postSchemaRegistration(String subject, String schema) throws JSONException {
        String registerUrl = schemaRegistryEndpoint + "subjects/" + subject + "/versions";
        JSONObject payload = new JSONObject().put("schema", schema);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(payload.toString(), headers);

        return restTemplate.postForEntity(registerUrl, entity, String.class);
    }

    private @NotNull ResponseEntity<String> getSchemaById(int id) {
        return restTemplate.getForEntity(schemaRegistryEndpoint + "schemas/ids/" + id + "?someQueryParam", String.class);
    }
}