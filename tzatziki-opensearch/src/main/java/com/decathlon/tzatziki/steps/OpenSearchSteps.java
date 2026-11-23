package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.config.OpenSearchConfiguration;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.generic.Requests;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.THAT;
import static org.opensearch.client.opensearch._types.HealthStatus.Yellow;

@Slf4j
@SuppressWarnings("java:S100") // Allow method names with underscores for BDD steps
public class OpenSearchSteps {
    private final OpenSearchClient openSearchClient;
    private final ObjectSteps objects;

    private static final OpenSearchConfiguration openSearchConfiguration =
            new OpenSearchConfiguration(System.getProperty("opensearch.host"));

    public OpenSearchSteps(ObjectSteps objects) {
        this.objects = objects;
        this.openSearchClient = openSearchConfiguration.getOpenSearchClient();
    }

    @AfterAll
    public static void afterAll() {
        openSearchConfiguration.close();
    }

    @After
    public void after() throws IOException {
        List<String> indicesToDelete = openSearchClient.indices()
                .get(g -> g.index("*"))
                .result()
                .keySet()
                .stream()
                .filter(indexName -> !indexName.startsWith("."))
                .toList();
        if (!indicesToDelete.isEmpty()) {
            openSearchClient.indices().delete(d -> d.index(indicesToDelete));
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

                var bulk = new BulkRequest.Builder()
                        .refresh(Refresh.WaitFor);

                docByIds.forEach((id, doc) ->
                        bulk.operations(op -> op.index(i -> i
                                .index(index)
                                .id(String.valueOf(id))
                                .document(doc))));

                openSearchClient.bulk(bulk.build());
            } catch (OpenSearchException | IOException e) {
                Assertions.fail(e.getMessage());
            }
        });
    }

    @Then(THAT + GUARD + "the ([^ ]+) index (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_index_contains(Guard guard, String index, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            try {
                List<Map<String, Object>> response = openSearchClient
                        .search(s -> s.index(index), Object.class)
                        .hits().hits().stream()
                        .map(hit -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> doc = (Map<String, Object>) hit.source();
                            List<Map> expectedDocs = Mapper.readAsAListOf(objects.resolve(content), Map.class);
                            if (!expectedDocs.isEmpty() && expectedDocs.get(0).containsKey("_id")) {
                                doc.put("_id", hit.id());
                            }
                            return doc;
                        })
                        .toList();

                comparison.compare(response, Mapper.readAsAListOf(objects.resolve(content), Map.class));
            } catch (OpenSearchException | IOException e) {
                Assertions.fail(e.getMessage());
            }
        });
    }

    @Then(THAT + GUARD + "the ([^ ]+) index mapping is:$")
    public void the_index_mapping_is(Guard guard, String index, Object content) {
        guard.in(objects, () -> {
            try {
                String response = openSearchClient.indices()
                        .getMapping(i -> i.index(index))
                        .get(index)
                        .mappings()
                        .toJsonString();

                Comparison.IS_EXACTLY.compare(Mapper.read(response), Mapper.read(objects.resolve(content)));
            } catch (OpenSearchException | IOException e) {
                Assertions.fail(e.getMessage());
            }
        });
    }

    @Given(THAT + GUARD + "the ([^ ]+) index is:$")
    public void the_index_is(Guard guard, String index, Object content) {
        guard.in(objects, () -> {
            try (var resp = openSearchClient.generic().execute(
                    Requests.builder()
                            .endpoint("/" + index)
                            .method("PUT")
                            .json(objects.resolve(content))
                            .build())) {
                openSearchClient.cluster().health(h -> h.index(index).waitForStatus(Yellow));
            } catch (IOException | OpenSearchException e) {
                Assertions.fail(e.getMessage());
            }
        });
    }
}