package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.config.OpenSearchConfiguration;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.THAT;
import static org.apache.hc.core5.http.Method.PUT;
import static org.opensearch.client.opensearch._types.HealthStatus.Yellow;

@Slf4j
public class OpenSearchSteps {
    private OpenSearchClient openSearchClient;
    private RestClient restClient;
    private final ObjectSteps objects;
    private OpenSearchConfiguration openSearchConfiguration;

    public OpenSearchSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    @Before
    public void setUp() {
        String opensearchHost = System.getProperty("opensearch.host");
        if (opensearchHost == null) {
            opensearchHost = objects.getOrDefault("opensearch.host", "localhost:9200");
        }

        openSearchConfiguration = new OpenSearchConfiguration(opensearchHost);
        openSearchClient = openSearchConfiguration.getOpenSearchClient();
        restClient = openSearchConfiguration.getRestClient();
    }

    @After
    public void after() throws IOException {
        try {
            openSearchClient.indices().delete(d -> d.index("_all"));
            openSearchClient.cluster().health(h -> h.waitForStatus(HealthStatus.Green));
        } finally {
            if (openSearchConfiguration != null) {
                openSearchConfiguration.close();
            }
        }
    }

    @Given(THAT + GUARD + "the ([^ ]+) index will contain:$")
    public void the_index_will_contain(Guard guard, String index, Object content) {
        guard.in(objects, () -> {
            try {
                Map<Object, Map> docByIds = Mapper.readAsAListOf(objects.resolve(content), Map.class).stream()
                        .collect(Collectors.toMap(doc -> doc.get("_id"), doc -> {
                            doc.remove("_id");
                            return doc;
                        }));
                Request request = new Request(PUT.name(), "/" + index + "/_bulk?refresh=wait_for");
                String body = docByIds.entrySet().stream().map(entry -> """
                                { "index" : { "_index": "%s", "_id" : "%s" } }
                                %s
                                """.formatted(index, entry.getKey(), Mapper.toJson(entry.getValue())))
                        .collect(Collectors.joining("\n"));
                request.setJsonEntity(body);
                restClient.performRequest(request);
            } catch (OpenSearchException | IOException e) {
                Assert.fail(e.getMessage());
            }
        });
    }

    @Then(THAT + GUARD + "the ([^ ]+) index (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_index_contains(Guard guard, String index, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            try {
                List<Map<String, Object>> response = openSearchClient.search(s -> s.index(index), Object.class)
                        .hits().hits().stream()
                        .map(hit -> {
                            Map<String, Object> doc = (Map<String, Object>) hit.source();
                            List<Map> expectedDocs = Mapper.readAsAListOf(objects.resolve(content), Map.class);
                            if (!expectedDocs.isEmpty() && expectedDocs.get(0).containsKey("_id")) {
                                doc.put("_id", hit.id());
                            }
                            return doc;
                        })
                        .collect(Collectors.toList());
                comparison.compare(response, Mapper.readAsAListOf(objects.resolve(content), Map.class));
            } catch (OpenSearchException | IOException e) {
                Assert.fail(e.getMessage());
            }
        });
    }

    @Then(THAT + GUARD + "the ([^ ]+) index mapping is:$")
    public void the_index_mapping_is(Guard guard, String index, Object content) {
        guard.in(objects, () -> {
            try {
                String response = openSearchClient.indices().getMapping(i -> i.index(index)).get(index).mappings().toJsonString();
                Comparison.IS_EXACTLY.compare(Mapper.read(response), Mapper.read(objects.resolve(content)));
            } catch (OpenSearchException | IOException e) {
                Assert.fail(e.getMessage());
            }
        });
    }

    @Given(THAT + GUARD + "the ([^ ]+) index is:$")
    public void the_index_is(Guard guard, String index, Object content) {
        guard.in(objects, () -> {
            try {
                Request request = new Request(PUT.name(), "/" + index);
                request.setJsonEntity(objects.resolve(content));
                restClient.performRequest(request);
                openSearchClient.cluster().health(h -> h.index(index).waitForStatus(Yellow));
            } catch (IOException | OpenSearchException e) {
                Assert.fail(e.getMessage());
            }
        });
    }
}
