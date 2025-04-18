package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.HttpUtils;
import com.decathlon.tzatziki.utils.Interaction;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;

import static com.decathlon.tzatziki.kafka.SchemaRegistryMockMigrationUtils.idTransformer;
import static com.decathlon.tzatziki.kafka.SchemaRegistryMockMigrationUtils.schemaTransformer;

public class SchemaRegistry {

    public static final MockSchemaRegistryClient CLIENT = new MockSchemaRegistryClient();

    public static String endpoint = "/schemaRegistry/";

    public static void initialize() {
        Interaction.Request requestId = Interaction.Request.builder().path(endpoint + "subjects/.+/versions")
                .method(com.decathlon.tzatziki.utils.Method.POST).build();
        Interaction interactionId = Interaction.builder().request(requestId).build();
        HttpUtils.mockInteraction(interactionId, Comparison.CONTAINS, idTransformer());


        Interaction.Request requestSchema = Interaction.Request.builder().path(endpoint + "schemas/ids/(.+)")
                .method(com.decathlon.tzatziki.utils.Method.GET).build();
        Interaction interactionSchema = Interaction.builder().request(requestSchema).build();
        HttpUtils.mockInteraction(interactionSchema, Comparison.CONTAINS, schemaTransformer());
    }

}
