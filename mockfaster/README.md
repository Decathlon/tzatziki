MockFaster
======

MockFaster! like MockServer ... but Fast!

## What is this thing about?

Mockserver can be quite slow at redefining mocks. Internally, it uses a client/server model that serializes all the requests.
MockFaster will use reflection to access the server part of mockserver and only create the handlers if they haven't been created yet.
That way, there is no performance cost in redefining a mock, it's only about swapping the internal handler.

You can use MockFaster as a direct replacement of MockServer, so:

```java
public static void initialize(ClientAndServer server) {
    server.when(request().withMethod("POST").withPath("/endpoint"))
            .respond(response().withStatusCode(201));
}
```

becomes:
```java
public static void initialize() {
    MockFaster.when(request().withMethod("POST").withPath("/endpoint"))
            .respond(response().withStatusCode(201));
}
```

Remember that since it is now a static, you will need to reset it between tests with:

```java
@BeforeEach
public void before(){
  MockFaster.reset();
}
```

## Port Configuration

By default, MockFaster's underlying MockServer starts on a random available port (dynamic port). You can specify a fixed port by setting the `tzatziki.mockfaster.port` system property:

```bash
# As a JVM argument
java -Dtzatziki.mockfaster.port=8888 -jar your-test.jar

# As a Maven property
mvn test -Dtzatziki.mockfaster.port=8888

# As a system property in your test setup
System.setProperty("tzatziki.mockfaster.port", "8888");
```

The port configuration follows this logic:
- If `tzatziki.mockfaster.port` is set to a valid integer, that port will be used
- If `tzatziki.mockfaster.port` is not set or is empty, a dynamic port will be used
- If `tzatziki.mockfaster.port` is set to an invalid value, an `IllegalArgumentException` will be thrown

**Note:** The port configuration takes effect when the `MockFaster` class is first loaded (static initialization). Once the MockServer is started, changing the system property will have no effect.


