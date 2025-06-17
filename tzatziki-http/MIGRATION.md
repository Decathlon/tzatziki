# ⚠️ Deprecation Notice

The legacy version of the tzatziki module (MockServer-based) is **deprecated** due to known security vulnerabilities (CVEs). We strongly recommend migrating to the WireMock-based module as described below. The legacy module will no longer be maintained and is scheduled for removal in a future release.

# Migration Guide: MockServer to WireMock

This guide helps you migrate from the MockServer-based implementation to the new WireMock-based implementation in tzatziki-http.

## Keep the Existing Behavior
The module previously named tzatziki-http (with MockServer implementation) has been renamed to tzatziki-http-mockserver-legacy.
To keep the existing behavior, simply update your pom.xml to use the new dependency name, no other changes are required.

## Dependency Changes

The `tzatziki-http` dependency is no longer automatically included as a transitive dependency of `tzatziki-spring` or `tzatziki-spring-jpa`. You must explicitly add `tzatziki-http` to your `pom.xml`.

If you are using the `tzatziki-http` module together with the `tzatziki-spring` module, you need to exclude the `cucumber-picocontainer` dependency from `tzatziki-http` to avoid conflicts. Example:

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

## Mockfaster Method Changes

Static methods from the `MockFaster` class, such as `url()` and `reset()`, have been migrated to the `HttpUtils` class. Please update your code to use `HttpUtils.url()` and `HttpUtils.reset()` instead of `MockFaster.url()` and `MockFaster.reset()`.

## Template Processing Changes

We are now using Wiremock internal Handlebar engine when possible. Escape curly braces when using Wiremock Handlebars syntax. 

### 1. Request Path Parameter Access

**MockServer**:
```gherkin
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """yml
  item_id: {{{[_request.pathParameterList.0.values.0.value]}}}
  """
```

**WireMock**:
```gherkin
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
"""yml
  item_id: \{{request.pathSegments.6}}
"""
```

### 2. Request Query Parameter Access

**MockServer**:
```gherkin
   Given that calling "http://backend/hello?name=.*" will return:
      """yml
      message: Hello {{{[_request.queryStringParameterList.0.values.0.value]}}}! # handlebars syntax for accessing arrays
      """
```

**WireMock**:
```gherkin
    Given that calling "http://backend/hello?name=.*" will return:
      """yml
      message: Hello \{{request.query.name}}! # handlebars syntax for accessing arrays
      """
```

### 3. Request Body Access

**MockServer**:
```gherkin
Given that posting on "http://backend/v1/resource/items" will return a List:
    """
      {{#foreach _request.body}}
      - id: {{this.id}}
        name: nameOf{{this.id}}
      {{/foreach}}
      """

Given that getting on "http://backend/endpoint" will return:
      """yml
      message: {{{[_request.body.json.text]}}}
      """
```

**WireMock**:
```gherkin
Given that posting on "http://backend/v1/resource/items" will return a List:
    """
      \{{#each (parseJson request.body)}}
      - id: \{{this.id}}
        name: nameOf\{{this.id}}
      \{{/each}}
      """
  
Given that getting on "http://backend/endpoint" will return:
      """yml
      message: \{{lookup (parseJson request.body) 'text'}}
      """
```

### 4. Template Helper Functions

**MockServer**:
```gherkin
Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
    """
    {{#split _request.pathParameterList.0.values.0.value [,]}}
    - item_id: {{this}}
    {{/split}}
    """
```

**WireMock**:
```gherkin
Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
    """
    \{{#split request.pathSegments.6 ','}}
    - item_id: \{{this}}
    \{{/split}}
    """
```

### 5. Wildcard Matching
Wildcards in paths are handled differently in WireMock. Instead of using `*` or `.*`, you must use `(.*)` for matching any segment.
**MockServer**:
```gherkin
And "http://backend/v1/resource/items/*" has received 0 POST
```

**WireMock**:
```gherkin
And "http://backend/v1/resource/items/(.*)" has received 0 POST
```

## Request Verification Changes

**MockServer**:
```gherkin
Then "http://backend/endpoint" has received 2 POST payloads
And payloads[0].body.json.containers[0].zones.size == 2
And payloads[1].body.json.containers[0].zones.size == 1
```

**WireMock**:
```gherkin
Then "http://backend/endpoint" has received 2 POST payloads
And payloads[0].request.body.containers[0].zones.size == 2
And payloads[1].request.body.containers[0].zones.size == 1
```

## Common Issues and Troubleshooting

### 1. Response verification object type sensitive
When verifying responses, ensure that the object types match. For example, if you are checking a string value, ensure that the value is indeed a string and not an integer or another type.

### 2. Path case sensitivity
WireMock is case-sensitive when it comes to paths. Ensure that the paths you are using in your tests match the actual paths in your application exactly, including case.

## Further Resources

For more details on WireMock's templating capabilities and syntax, refer to:
[WireMock Response Templating](http://wiremock.org/docs/response-templating/)

If you encounter any specific migration issues, please open an issue on the [tzatziki GitHub repository](https://github.com/Decathlon/tzatziki/issues).
