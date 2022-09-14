package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Mapper;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import org.apache.avro.Schema;

import java.util.Map;

import static com.decathlon.tzatziki.utils.MockFaster.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class SchemaRegistry {

    private static final MockSchemaRegistryClient CLIENT = new MockSchemaRegistryClient();

    public static String endpoint = "/schemaRegistry/";

    public static void initialize() {
        when(request(endpoint + "subjects/.+/versions").withMethod("POST"), Comparison.CONTAINS)
                .respond(req -> {
                    String subject = req.getPath().toString().replaceAll(endpoint + "subjects/(.+)/versions", "$1");
                    Schema schema = new Schema.Parser().parse(Mapper.<Map<String, String>>read(req.getBodyAsString()).get("schema"));
                    int id = CLIENT.register(subject, schema);
                    return response().withStatusCode(200).withBody(Mapper.toJson(Map.of("id", id)));
                });

        when(request(endpoint + "schemas/ids/(.+)"), Comparison.CONTAINS)
                .respond(req -> {
                    int id = Integer.parseInt(req.getPath().toString().replaceAll(endpoint + "schemas/ids/(.+)", "$1"));
                    Schema schema = CLIENT.getById(id);
                    return response().withStatusCode(200).withBody(Mapper.toJson(Map.of("schema", schema.toString())));
                });
    }
}
