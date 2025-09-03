package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.HttpUtils;
import com.decathlon.tzatziki.utils.Interaction;
import com.decathlon.tzatziki.utils.Mapper;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import lombok.SneakyThrows;
import org.apache.avro.Schema;

import java.util.Map;

public class SchemaRegistry {

    public static final MockSchemaRegistryClient CLIENT = new MockSchemaRegistryClient();

    public static String endpoint = "/schemaRegistry/";

    public static void initialize() {
        Interaction.Request requestId = Interaction.Request.builder().path(endpoint + "subjects/.+/versions")
                .method(com.decathlon.tzatziki.utils.Method.POST).build();
        Interaction interactionId = Interaction.builder().request(requestId).build();
        HttpUtils.mockInteraction(interactionId, Comparison.CONTAINS, (SchemaRegistry::mockId
        ));


        Interaction.Request requestSchema = Interaction.Request.builder().path(endpoint + "schemas/ids/(.+)")
                .method(com.decathlon.tzatziki.utils.Method.GET).build();
        Interaction interactionSchema = Interaction.builder().request(requestSchema).build();
        HttpUtils.mockInteraction(interactionSchema, Comparison.CONTAINS, SchemaRegistry::mockSchema);
    }

    @SneakyThrows
    private static Interaction.Response mockId(Interaction.Request request) {
        String subject = request.path.replaceAll(endpoint + "subjects/(.+?)/versions.*", "$1");
        Schema schema = new Schema.Parser().parse(Mapper.<Map<String, String>>read(request.body.toString(null)).get("schema"));
        int id = CLIENT.register(subject, schema);
        String responseBody = Mapper.toJson(Map.of("id", id));
        return Interaction.Response.builder()
                .status("200")
                .body(Interaction.Body.builder().payload(responseBody).build())
                .build();
    }


    @SneakyThrows
    private static Interaction.Response mockSchema(Interaction.Request request) {
        int id = Integer.parseInt(request.path.replaceAll(endpoint + "schemas/ids/([^?]+).*", "$1"));
        Schema schema = CLIENT.getById(id);
        String responseBody = Mapper.toJson(Map.of("schema", schema.toString()));
        return Interaction.Response.builder()
                .status("200")
                .body(Interaction.Body.builder().payload(responseBody).build())
                .build();
    }

}
