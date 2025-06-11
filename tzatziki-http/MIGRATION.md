# Migration Guide: MockServer to WireMock

This guide helps you migrate from the MockServer-based implementation to the new WireMock-based implementation in tzatziki-http.

## Dependency Changes

First, update your dependencies:

```xml
<!-- Remove old dependency -->
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-http-mockserver-legacy</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>

<!-- Add new dependency -->
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-http</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

## URL Remapping Changes

MockServer and WireMock have different URL remapping schemes:

- **MockServer**: `http://localhost:<MockFaster.localPort()>/http/backend/users`
- **WireMock**: `http://localhost:<wireMockServer.port()>/_mocked/http/backend/users`

If you're directly accessing the mocked URLs (rather than using the remapped URLs), you'll need to update them.

## Template Processing Changes

### 1. Request Object Access

**MockServer**:
```gherkin
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """
  item_id: {{_request.pathParameterList.0.values.0}}
  """
```

**WireMock**:
```gherkin
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """
  item_id: {{request.pathSegments.6}}
  """
```

### 2. Request Body Access

**MockServer**:
```gherkin
Given that posting on "http://backend/v1/resource/items" will return a List:
  """
  {{#foreach _request.body}}
  - id: {{this.id}}
    name: nameOf{{this.id}}
  {{/foreach}}
  """
```

**WireMock**:
```gherkin
Given that posting on "http://backend/v1/resource/items" will return a List:
  """
  {{#each (parseJson request.body)}}
  - id: {{this.id}}
    name: nameOf{{this.id}}
  {{/each}}
  """
```

### 3. Template Helper Functions

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
  {{#split request.pathSegments.6 ','}}
  - item_id: {{this}}
  {{/split}}
  """
```

## Request Verification Changes

### 1. Accessing Saved Requests

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

### 2. Response Fields Access

Response field access has changed, and the paths into the response objects are different.

## WireMock-Specific Features

### Header Matching with Comparison Operators

WireMock provides powerful header matching capabilities with comparison operators:

```gherkin
Given that "http://backend/headers" is mocked as:
  """yml
  request:
    method: GET
    headers:
      exact-match: ?eq expected-value
      regex-match: ?e value-[0-9]+
      contains-match: ?contains contains-this
      not-contains-match: ?doesNotContain without-this
      greater-than: ?gt 100
      greater-equal: ?ge 100
      less-than: ?lt 100
      less-equal: ?le 100
      not-equal1: ?not unexpected-value
      not-equal2: ?ne unexpected-value
      not-equal3: ?!= unexpected-value
      in-list: ?in ['value1', 'value2', 'value3']
      not-in-list: ?notIn ['banned1', 'banned2']
      uuid-value: ?isUUID
      null-header: ?isNull
      not-null-header: ?notNull
      date-before: ?before {{@now}}
      date-after: ?after {{@now}}
  response:
    status: OK_200
  """
```

## Common Issues and Troubleshooting

### 1. Template Syntax Differences

WireMock uses Handlebars for templating. Escape curly braces when using variables:

```
item_id: \{{request.pathSegments.6}}
```

### 2. Path Parameter Access

MockServer and WireMock access path parameters differently:

- MockServer uses `pathParameterList.0.values.0`
- WireMock uses `request.pathSegments.N` where N is the position in the URL path

### 3. Query Parameter Access

For query parameters:

- MockServer: `_request.queryStringParameters.paramName.0`
- WireMock: `request.query.paramName`

## Examples

### Complete Example: URL Pattern Matching with Parameters

**MockServer**:
```gherkin
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """
  item_id: {{_request.pathParameterList.0.values.0}}
  userAgent: {{_request.headers.User-Agent.0}}
  """
```

**WireMock**:
```gherkin
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """
  item_id: {{request.pathSegments.6}}
  userAgent: {{request.headers.User-Agent}}
  """
```

### Complete Example: Processing Request Body

**MockServer**:
```gherkin
Given that posting on "http://backend/users" will return:
  """
  {{#foreach _request.body}}
  - name: {{this.name}}
    processed: true
  {{/foreach}}
  """
```

**WireMock**:
```gherkin
Given that posting on "http://backend/users" will return:
  """
  {{#each (parseJson request.body)}}
  - name: {{this.name}}
    processed: true
  {{/each}}
  """
```

## Further Resources

For more details on WireMock's capabilities and syntax, refer to:

1. [WireMock Documentation](http://wiremock.org/docs/)
2. [WireMock Request Matching](http://wiremock.org/docs/request-matching/)
3. [WireMock Response Templating](http://wiremock.org/docs/response-templating/)
4. [Handlebars.js Documentation](https://handlebarsjs.com/) for template syntax

If you encounter any specific migration issues, please open an issue on the [tzatziki GitHub repository](https://github.com/Decathlon/tzatziki).
