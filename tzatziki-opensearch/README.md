Tzatziki OpenSearch
======

## Description

This module provides Cucumber steps for testing applications that use OpenSearch. It allows you to set up OpenSearch indices with test data and verify their contents using Gherkin syntax, making integration testing with OpenSearch simple and readable.

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-opensearch</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

Make sure that you have the bootstrap class described in the [Core README](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-core#get-started-with-this-module)

## Configuration

The module automatically configures an OpenSearch client using the system property `opensearch.host`. You can set this property in your test configuration:

```java
System.setProperty("opensearch.host", "http://localhost:9200");
```

For integration tests, you can use OpenSearch TestContainers.

### Testcontainers Dependency

The module includes support for Testcontainers through the `opensearch-testcontainers` library, which is compatible with Testcontainers 2.x:

```xml
<dependency>
    <groupId>org.opensearch</groupId>
    <artifactId>opensearch-testcontainers</artifactId>
    <version>2.1.4</version>
    <scope>test</scope>
</dependency>
```

This dependency transitively includes `org.testcontainers:testcontainers:2.0.3` (or higher).

## Features

### Setting up test data

You can populate OpenSearch indices with test data using the following step:

```gherkin
Given that the users index will contain:
  """yaml
  - _id: user1
    name: John Doe
    email: john@example.com
    age: 30
  - _id: user2
    name: Jane Smith
    email: jane@example.com
    age: 25
  """
```

The `_id` field is used as the document ID in OpenSearch and is removed from the document source.

### Verifying index contents

You can verify that an index contains expected documents:

```gherkin
Then the users index contains:
  """yaml
  - _id: user1
    name: John Doe
    email: john@example.com
    age: 30
  """
```

### Comparison modes

The module supports different comparison modes for flexible assertions:

```gherkin
# Exact match (default)
Then the users index contains:
  """yaml
  - name: John Doe
    email: john@example.com
  """

# Contains (partial match)
Then the users index contains at least:
  """yaml
  - name: John Doe
  """

# Using guards for conditional execution
Then if the user exists, the users index contains:
  """yaml
  - name: John Doe
  """
```

## Automatic cleanup

The module automatically cleans up test indices after each test method, ensuring test isolation. System indices (those starting with `.`) are preserved.

## Dependencies

This module includes:
- OpenSearch Java Client (`opensearch-java`)
- OpenSearch REST Client (`opensearch-rest-client`)
- Jackson for JSON serialization (via `tzatziki-jackson`)
- Tzatziki Core utilities

For testing, it also includes:
- OpenSearch TestContainers for integration testing

## Troubleshooting

### Connection issues
Make sure your OpenSearch instance is running and accessible at the configured host URL.

### Version compatibility
This module is tested with OpenSearch 2.x. For other versions, you may need to adjust the client dependencies.

### TestContainers setup
When using TestContainers, ensure Docker is running and accessible from your test environment.
