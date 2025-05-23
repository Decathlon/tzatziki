package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.utils.Mapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import lombok.SneakyThrows;
import org.apache.avro.Schema;

import java.util.Map;

import static com.decathlon.tzatziki.kafka.SchemaRegistry.endpoint;

public class SchemaRegistryResponseTransformer implements ResponseDefinitionTransformerV2 {

    private static final MockSchemaRegistryClient CLIENT = new MockSchemaRegistryClient();

    @Override
    public String getName() {
        return "schema-registry-response-transformer";
    }

    @SneakyThrows
    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        if (request.getUrl().contains(endpoint + "subjects/")) {
            String subject = request.getUrl().replaceAll(endpoint + "subjects/(.+?)/versions.*", "$1");
            Schema schema = new Schema.Parser().parse(Mapper.<Map<String, String>>read(request.getBodyAsString()).get("schema"));
            int id = CLIENT.register(subject, schema);
            String responseBody = Mapper.toJson(Map.of("id", id));
            return new ResponseDefinitionBuilder()
                    .withStatus(200)
                    .withBody(responseBody)
                    .build();
        } else {
            int id = Integer.parseInt(request.getUrl().replaceAll(endpoint + "schemas/ids/(.+?)(\\?.*)?", "$1"));
            Schema schema = CLIENT.getById(id);
            String responseBody = Mapper.toJson(Map.of("schema", schema.toString()));
            return new ResponseDefinitionBuilder()
                    .withStatus(200)
                    .withBody(responseBody)
                    .build();
        }
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }
}