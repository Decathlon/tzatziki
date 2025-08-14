package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.THAT;
import static org.apache.hc.core5.http.Method.PUT;
import static org.opensearch.client.opensearch._types.HealthStatus.Yellow;

@RequiredArgsConstructor
@Slf4j
public class OpenSearchSteps {
    private final OpenSearchClient openSearchClient;
    private final RestClient restClient;
    private final ObjectSteps objects;

    @After
    public void after() throws IOException {
        DeleteIndexResponse deleteIndexResponse = openSearchClient.indices().delete(d -> d.index("_all"));
        if (!deleteIndexResponse.acknowledged()) {
            throw new RuntimeException("Index deletion not acknowledged!");
        }
        openSearchClient.cluster().health(h -> h.waitForStatus(HealthStatus.Green));
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
                List<Object> response = openSearchClient.search(s -> s.index(index), Object.class).hits().hits().stream().map(Hit::source).toList();
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
