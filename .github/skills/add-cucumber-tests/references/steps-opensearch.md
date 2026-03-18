# User Provided Header
Tzatziki OpenSearch module reference.
- OpenSearchSteps.java defines @Given/@When/@Then patterns for OpenSearch index management, document indexing, and search assertions.
- .feature files demonstrate valid OpenSearch step usage.


# Directory Structure
```
tzatziki-opensearch/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                OpenSearchSteps.java
    test/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                OpensearchContainerSteps.java
      resources/
        features/
          opensearch.feature
```

# Files

## File: tzatziki-opensearch/src/main/java/com/decathlon/tzatziki/steps/OpenSearchSteps.java
```java
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
```

## File: tzatziki-opensearch/src/test/java/com/decathlon/tzatziki/steps/OpensearchContainerSteps.java
```java
package com.decathlon.tzatziki.steps;

import io.cucumber.java.Before;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class OpensearchContainerSteps {

    private static OpensearchContainer<?> opensearch;

    @Before(order = -1)
    public void startOpenSearchContainer() {
        if (opensearch == null) {
            opensearch = new OpensearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.11.0"))
                    .withEnv("action.auto_create_index", "false");
            opensearch.start();

            String hostAddress = opensearch.getHttpHostAddress();
            System.setProperty("opensearch.host", hostAddress);
            log.info("OpenSearch container started at: {}", hostAddress);
        }
    }

    public static void stopContainer() {
        if (opensearch != null && opensearch.isRunning()) {
            opensearch.stop();
            opensearch = null;
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(OpensearchContainerSteps::stopContainer));
    }
}
```

## File: tzatziki-opensearch/src/test/resources/features/opensearch.feature
```
Feature: Interact with a spring boot application that uses OpenSearch as a persistence layer

  Background:

  Scenario: Define users index and insert a user document
    Given that the users index is:
    """json
    {
      "settings": {
        "number_of_shards": "1",
        "number_of_replicas": "2"
      },
      "mappings": {
        "properties": {
          "firstName": {
            "type": "keyword"
          },
          "lastName": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the users index will contain:
      | _id | firstName | lastName |
      | 1   | Darth     | Vader    |

    Then the users index contains:
      | _id | firstName | lastName |
      | 1   | Darth     | Vader    |

  Scenario: Test index mapping verification
    Given that the products index is:
    """json
    {
      "mappings": {
        "properties": {
          "name": {
            "type": "text"
          },
          "price": {
            "type": "double"
          },
          "category": {
            "type": "keyword"
          },
          "tags": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Then the products index mapping is:
    """json
    {
      "properties": {
        "name": {
          "type": "text"
        },
        "price": {
          "type": "double"
        },
        "category": {
          "type": "keyword"
        },
        "tags": {
          "type": "keyword"
        }
      }
    }
    """

  Scenario: Insert multiple documents with custom IDs
    Given that the orders index is:
    """json
    {
      "settings": {
        "number_of_shards": "1"
      },
      "mappings": {
        "properties": {
          "orderId": {
            "type": "keyword"
          },
          "amount": {
            "type": "double"
          },
          "status": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the orders index will contain:
      | _id    | orderId | amount | status    |
      | order1 | ORD001  | 99.99  | completed |
      | order2 | ORD002  | 149.50 | pending   |
      | order3 | ORD003  | 75.25  | cancelled |

    Then the orders index contains:
      | orderId | amount | status    |
      | ORD001  | 99.99  | completed |
      | ORD002  | 149.50 | pending   |
      | ORD003  | 75.25  | cancelled |

  Scenario: Test different comparison modes
    Given that the inventory index is:
    """json
    {
      "mappings": {
        "properties": {
          "productId": {
            "type": "keyword"
          },
          "quantity": {
            "type": "integer"
          }
        }
      }
    }
    """
    Given that the inventory index will contain:
      | _id | productId | quantity |
      | 1   | PROD001   | 50       |
      | 2   | PROD002   | 25       |
      | 3   | PROD003   | 100      |

    Then the inventory index contains exactly:
      | productId | quantity |
      | PROD001   | 50       |
      | PROD002   | 25       |
      | PROD003   | 100      |

    Then the inventory index still contains:
      | productId | quantity |
      | PROD001   | 50       |
      | PROD002   | 25       |

  Scenario: Test nested objects and complex data types
    Given that the articles index is:
    """json
    {
      "mappings": {
        "properties": {
          "title": {
            "type": "text"
          },
          "author": {
            "properties": {
              "name": {
                "type": "keyword"
              },
              "email": {
                "type": "keyword"
              }
            }
          },
          "publishDate": {
            "type": "date"
          },
          "tags": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the articles index will contain:
      | _id | title                    | author                                      | publishDate | tags                    |
      | 1   | Introduction to OpenSearch | {"name":"John Doe","email":"john@test.com"} | 2024-01-15  | ["search","tutorial"]   |

    Then the articles index contains:
      | title                    | author                                      | publishDate | tags                    |
      | Introduction to OpenSearch | {"name":"John Doe","email":"john@test.com"} | 2024-01-15  | ["search","tutorial"]   |

  Scenario: Test empty index
    Given that the empty_test index is:
    """json
    {
      "mappings": {
        "properties": {
          "field1": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Then the empty_test index contains exactly:
      | field1 |
```
