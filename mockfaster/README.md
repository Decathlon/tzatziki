MockFaster
======

MockFaster! like MockServer ... but Fast!

## What is this thing about?

Mockserver can be quite slow at redefining mocks. Internally, it uses a client/server model that serializes all the requests.
MockFaster will use reflection to access the server part of mockserver and only create the handlers if they haven't been created yet.
That way, there is no performance cost in redifining a mock, it's only about swapping the internal handler.

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


