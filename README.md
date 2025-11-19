# TerseRest

A terse wrapper for OkHttp providing minimal ceremony REST API calls in Java.

## Why?

Java REST calls are verbose. This fixes that with fluent JSON builders and clean error handling.

**Before:**
```java
var client = new OkHttpClient();
var json = new ObjectMapper().writeValueAsString(Map.of("name", "Alice"));
var request = new Request.Builder()
    .url("https://api.example.com/users")
    .post(RequestBody.create(json, MediaType.get("application/json")))
    .build();
```

**After:**
```java
req("https://api.example.com/users")
    .post()
    .body(obj("name", "Alice", "age", 30))
    .json(User.class);
```

## Quick Start

**Maven:**
```xml
<dependency>
    <groupId>io.github.xyz-jphil</groupId>
    <artifactId>terserest</artifactId>
    <version>1.0</version>
</dependency>
```

**Import:**
```java
import static terse.Rest.*;
```

## Examples

**Simple:**
```java
var post = req("https://api.example.com/posts/1")
    .json(Post.class)
    .orElseThrow();
```

**POST with JSON:**
```java
req("https://api.example.com/users")
    .post()
    .bearer("token")
    .body(obj(
        "name", "Alice",
        "address", obj("city", "NYC"),
        "tags", arr("admin", "verified")
    ))
    .json(User.class)
    .handle(
        success -> System.out.println("Created: " + success.data()),
        failure -> System.err.println("Failed: " + failure.message())
    );
```

**Error Handling (Simple):**
```java
switch (result) {
    case Success<Post> s -> System.out.println(s.data());
    case Failure f -> System.err.println(f.message());
}
```

**Error Handling (Detailed):**
```java
switch (result) {
    case Success<Post> s -> handleSuccess(s);
    case NetworkFailure nf -> retry(); // Network error, can retry
    case HttpFailure hf -> logError(hf.status()); // HTTP 4xx/5xx
    case ParseFailure pf -> reportBug(pf.cause()); // JSON parse error
}
```

**Structured Error Responses:**
```java
case HttpFailure hf -> {
    hf.parseAs(ApiError.class).ifPresent(err ->
        System.err.println("API Error: " + err.message())
    );
}
```

**Authentication:**
```java
req(url).bearer("token").json(Data.class);
req(url).basic("user", "pass").send();
req(url).cookie("session", "abc123").send();
```

See `src/test/java/terse/TerseHttpExample.java` for more examples.

## Requirements

- Java 21+ (sealed types, pattern matching)
- Dependencies: OkHttp 4.12.0, Jackson 2.18.2
