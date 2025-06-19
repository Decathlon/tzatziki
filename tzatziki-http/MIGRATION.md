# Migration Guide: from MockServer to WireMock in Tzatziki

This guide provides step-by-step instructions for migrating your test suites from the legacy MockServer based module to the modern WireMock based implementation in `tzatziki-http`.

## ⚠️ Deprecation Notice & Rationale

The legacy `tzatziki-http` module, which used MockServer, is **deprecated**.

* **Security:** The MockServer dependency has known security vulnerabilities (CVEs).
* **Maintenance:** The legacy module will no longer receive updates or bug fixes.
* **Future-Proofing:** The new module leverages WireMock's powerful features, active development, and extensive community support.

We strongly recommend migrating to the new WireMock based module to ensure your test environment is secure, stable, and maintainable. The legacy module is scheduled for complete removal in a future release.

## How to Migrate

### Option A: Keep the Legacy Behavior (Not Recommended)

If you need to temporarily postpone migration, you can continue using the MockServer implementation by renaming your dependency. The module has been renamed from `tzatziki-http` to `tzatziki-http-mockserver-legacy`.

Simply update your `pom.xml` to use the new artifact ID. No other code changes are required.

### Option B: Migrate to WireMock (Recommended)

Follow these steps to update your project to the new `tzatziki-http` module.

#### 1. Update Dependencies

The new `tzatziki-http` module is no longer a transitive dependency of `tzatziki-spring` or `tzatziki-spring-jpa`. You must now manage it explicitly in your `pom.xml`.

If you are using `tzatziki-http` alongside `tzatziki-spring`, you must also exclude the `cucumber-picocontainer` dependency from `tzatziki-http` to prevent dependency conflicts.

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-http</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-picocontainer</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

#### 2. Update Static Method Calls

The static utility methods previously found in the `MockFaster` class have been moved to `HttpUtils`.

* Update `MockFaster.url()` to `HttpUtils.url()`
* Update `MockFaster.reset()` to `HttpUtils.reset()`

#### 3. Adapt to WireMock Templating

The most significant changes are in the response templating syntax, as WireMock uses a different expression language and data model than MockServer.

**Important:** WireMock's templating uses Handlebars syntax (`{{...}}`). Because Tzatziki also processes these files, you must **escape** the Handlebars expressions with a backslash (`\`) to ensure they are passed correctly to WireMock.

**Example:** `{{{...}}}` becomes `\{{{...}}}`.

#### Key Syntax Differences: At a Glance

| Feature              | MockServer Syntax                             | WireMock Syntax                                   |
| -------------------- | --------------------------------------------- | ------------------------------------------------- |
| **Path Parameter** | `_request.pathParameterList.0.values.0`       | `request.pathSegments.N`                          |
| **Query Parameter** | `_request.queryStringParameterList.0.values.0`| `request.query.paramName`                         |
| **JSON Body** | `_request.body.json.fieldName`                | `(lookup (parseJson request.body) 'fieldName')`   |
| **JSON Array Iteration** | `{{#foreach _request.body}}`                  | `\{{#each (parseJson request.body)}}`              |
| **Wildcard in Path** | `/items/*`                                    | `/items/(.*)`                                     |
| **Verification Path**| `payloads[0].body.json.field`                 | `payloads[0].request.body.field`                  |

### Detailed Templating Examples

#### Path Parameter Access

**WireMock** uses the `request.pathSegments` array to access path parameters. Each segment of the path (split by `/`) is available as an element in this array, starting from index 0. For example, in the path `/v1/resource/item/123`, `request.pathSegments.0` is `v1`, `request.pathSegments.1` is `resource`, `request.pathSegments.2` is `item`, and `request.pathSegments.3` is `123`. You can use these indices to extract specific path parameters in your templates.

**Before (MockServer)**

```gherkin
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """yml
  item_id: {{{[_request.pathParameterList.0.values.0.value]}}}
  """
```

**After (WireMock)**

```gherkin
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
"""yml
  item_id: \{{request.pathSegments.6}}
"""
```

#### Query Parameter Access

**WireMock** provides a `request.query` object where each key corresponds to a query parameter name.

**Before (MockServer)**

```gherkin
Given that calling "http://backend/hello?name=.*" will return:
  """yml
  message: Hello {{{[_request.queryStringParameterList.0.values.0.value]}}}!
  """
```

**After (WireMock)**

```gherkin
Given that calling "http://backend/hello?name=.*" will return:
  """yml
  message: Hello \{{request.query.name}}!
  """
```

#### Request Body Access (JSON)

With **WireMock**, you must first parse the raw request body using the `parseJson` helper. You can then access its properties.

**Before (MockServer)**

```gherkin
# Accessing a single field
Given that getting on "http://backend/endpoint" will return:
  """yml
  message: {{{[_request.body.json.text]}}}
  """

# Iterating over a list
Given that posting on "http://backend/v1/resource/items" will return a List:
  """
  {{#foreach _request.body}}
  - id: {{this.id}}
    name: nameOf{{this.id}}
  {{/foreach}}
  """
```

**After (WireMock)**

```gherkin
# Accessing a single field
Given that getting on "http://backend/endpoint" will return:
  """yml
  message: \{{lookup (parseJson request.body) 'text'}}
  """

# Iterating over a list
Given that posting on "http://backend/v1/resource/items" will return a List:
  """
  \{{#each (parseJson request.body)}}
  - id: \{{this.id}}
    name: nameOf\{{this.id}}
  \{{/each}}
  """
```

#### Template Helper Functions (`split`)

**Before (MockServer)**

```gherkin
Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
  """
  {{#split _request.pathParameterList.0.values.0.value [,]}}
  - item_id: {{this}}
  {{/split}}
  """
```

**After (WireMock)**

```gherkin
Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
  """
  \{{#split request.pathSegments.6 ','}}
  - item_id: \{{this}}
  \{{/split}}
  """
```

#### 4. Adjust Path Matching (Wildcards)

WireMock uses regex capturing groups for wildcards. Replace trailing `*` or `.*` with `(.*)`.

**Before (MockServer)**

```gherkin
And "http://backend/v1/resource/items/*" has received 0 POST
```

**After (WireMock)**

```gherkin
And "http://backend/v1/resource/items/(.*)" has received 0 POST
```

#### 5. Update Request Verification

When verifying received payloads, the path to the request body has changed slightly. The body content is now nested inside a `request` object.

**Before (MockServer)**

```gherkin
Then "http://backend/endpoint" has received 2 POST payloads
And payloads[0].body.json.containers[0].zones.size == 2
And payloads[1].body.json.containers[0].zones.size == 1
```

**After (WireMock)**

```gherkin
Then "http://backend/endpoint" has received 2 POST payloads
And payloads[0].request.body.containers[0].zones.size == 2
And payloads[1].request.body.containers[0].zones.size == 1
```

## Common Issues & Troubleshooting

1.  **Response Verification & Type Sensitivity:** WireMock is strict about data types in JSON assertions. If a test fails, verify that you are comparing against the correct type (e.g., `123` vs `"123"`).
2.  **Path Case Sensitivity:** WireMock paths are case-sensitive by default. Ensure the path in your Gherkin step exactly matches the path in your application code.

## Further Resources

* **Official Documentation:** [WireMock Response Templating](http://wiremock.org/docs/response-templating/)
* **Report an Issue:** If you encounter a problem specific to this migration, please open an issue on the [Tzatziki GitHub repository](https://github.com/Decathlon/tzatziki/issues).
