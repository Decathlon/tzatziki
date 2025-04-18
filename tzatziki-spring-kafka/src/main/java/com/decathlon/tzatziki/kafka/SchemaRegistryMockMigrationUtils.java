package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.utils.Mapper;
import org.apache.avro.Schema;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.decathlon.tzatziki.kafka.SchemaRegistry.CLIENT;
import static com.decathlon.tzatziki.kafka.SchemaRegistry.endpoint;

public class SchemaRegistryMockMigrationUtils {

    public static Object idTransformer() {
        try {
            Class.forName("com.decathlon.tzatziki.utils.HttpWiremockUtils");
            return "schema-registry-response-transformer";
        } catch (ClassNotFoundException e) {
            UnaryOperator<Object> supplier = req -> {
                try {
                    Class<?> responseClass = Class.forName("org.mockserver.model.HttpResponse");
                    Method responseMethod = responseClass.getMethod("response");
                    Method getPathMethod = req.getClass().getMethod("getPath");
                    String path = getPathMethod.invoke(req).toString();
                    String subject = path.replaceAll(endpoint + "subjects/(.+)/versions", "$1");

                    Method getBodyAsStringMethod = req.getClass().getMethod("getBodyAsString");
                    String body = (String) getBodyAsStringMethod.invoke(req);

                    Schema schema = new Schema.Parser().parse(Mapper.<Map<String, String>>read(body).get("schema"));
                    int id = CLIENT.register(subject, schema);

                    Object responseObj = responseMethod.invoke(null);
                    Method withStatusCodeMethod = responseObj.getClass().getMethod("withStatusCode", Integer.class);
                    Method withBodyMethod = withStatusCodeMethod.invoke(responseObj, 200).getClass().getMethod("withBody", String.class);

                    return withBodyMethod.invoke(withStatusCodeMethod.invoke(responseObj, 200), Mapper.toJson(Map.of("id", id)));
                } catch (Exception ex) {
                    throw new RuntimeException("Error in respond lambda", ex);
                }
            };
            return supplier;
        }

    }

    public static Object schemaTransformer() {
        try {
            Class.forName("com.decathlon.tzatziki.utils.HttpWiremockUtils");
            return "schema-registry-response-transformer";
        } catch (ClassNotFoundException e) {
            UnaryOperator<Object> supplier = req -> {
                try {
                    Class<?> responseClass = Class.forName("org.mockserver.model.HttpResponse");
                    Method responseMethod = responseClass.getMethod("response");
                    Method getPathMethod = req.getClass().getMethod("getPath");
                    String path = getPathMethod.invoke(req).toString();
                    int id = Integer.parseInt(path.replaceAll(endpoint + "schemas/ids/(.+)", "$1"));

                    Schema schema = CLIENT.getById(id);

                    Object responseObj = responseMethod.invoke(null);
                    Method withStatusCodeMethod = responseObj.getClass().getMethod("withStatusCode", Integer.class);
                    Method withBodyMethod = withStatusCodeMethod.invoke(responseObj, 200).getClass().getMethod("withBody", String.class);

                    return withBodyMethod.invoke(withStatusCodeMethod.invoke(responseObj, 200), Mapper.toJson(Map.of("schema", schema.toString())));
                } catch (Exception ex) {
                    throw new RuntimeException("Error in respond lambda", ex);
                }
            };
            return supplier;
        }

    }
}
