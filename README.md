# TerseRest

A terse wrapper for OkHttp providing minimal ceremony REST API calls in Java.

**GitHub:** https://github.com/xyz-jphil/terserest
**Maven Central:** https://central.sonatype.com/artifact/io.github.xyz-jphil/terserest

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
    <version>1.2</version>
</dependency>
```

**Import:**
```java
import static terse.Rest.*;
import static terse.Json.*;  // Required for obj() and arr() in version 1.2+
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

**Exploratory Testing with JsonNode:**

Use `jsonNode()` when you don't want to define records upfront:

```java
// Explore API response structure
var result = req("https://api.example.com/data")
    .jsonNode()
    .orElseThrow();

System.out.println(result.toPrettyString());
```

```java
// Extract specific fields without defining records
var name = req("https://api.example.com/users/1")
    .jsonNode()
    .map(node -> node.get("name").asText())
    .orElseThrow();
```

```java
// Handle dynamic response structures
req(url).jsonNode().handle(
    success -> {
        var node = success.data();
        if (node.has("error")) {
            handleError(node.get("error"));
        } else {
            processData(node.get("result"));
        }
    },
    failure -> System.err.println(failure.message())
);
```

See `src/test/java/terse/TerseHttpExample.java` for more examples.

## Upgrading from 1.1 to 1.2

**What changed:** JSON builders (`obj()`, `arr()`, `JsonObj`, `JsonArr`) moved to separate library `tersejson`.

**Why:** Clean separation of concerns - `tersejson` provides JSON building without HTTP dependencies, `terserest` focuses on HTTP client functionality.

**Migration steps:**

1. **Update terserest version:**
```xml
<dependency>
    <groupId>io.github.xyz-jphil</groupId>
    <artifactId>terserest</artifactId>
    <version>1.2</version>  <!-- Updated from 1.1 -->
</dependency>
```

2. **Add one import line to your code:**
```java
import static terse.Rest.*;
import static terse.Json.*;  // ADD THIS LINE
```

That's it! Your code remains unchanged - `obj()` and `arr()` work exactly the same way.

**Using tersejson standalone:**

If you only need JSON building (without HTTP client), use `tersejson` directly:

```xml
<dependency>
    <groupId>io.github.xyz-jphil</groupId>
    <artifactId>tersejson</artifactId>
    <version>1.0</version>
</dependency>
```

```java
import static terse.Json.*;

// Build JSON with any HTTP client (OkHttp, Java HttpClient, etc.)
String jsonString = obj(
    "name", "Alice",
    "age", 30,
    "tags", arr("admin", "verified")
).toString();
```

## Requirements

- Java 21+ (sealed types, pattern matching)
- Dependencies: OkHttp 4.12.0, Jackson 2.18.2
