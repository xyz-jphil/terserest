package terse;

import static terse.Rest.*;
import static terse.Json.*;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Examples demonstrating the usage of TerseRest library.
 *
 * This class shows various ways to use the terse HTTP client for making API calls.
 *
 * Example 9 demonstrates 4 variations of JSON mapping:
 * 1. Record (strict) - all fields must match API response
 * 2. Record with @JsonIgnoreProperties - ignores unknown fields, access via rawJson
 * 3. Lombok @Data with fluent accessors - mutable with complete fields
 * 4. Lombok with @JsonIgnoreProperties - mutable, ignores unknown fields
 *
 * Key Feature: Success includes rawJson (JsonNode) to access fields not in your POJO:
 *   success.data()       - Your typed object
 *   success.rawJson()    - Complete JSON response for accessing missing fields
 *
 * Note: Lombok fluent accessors require @JsonAutoDetect(fieldVisibility = ANY)
 */
public class TerseHttpExample {

    // Control which examples to run (set to false to skip)
    static final boolean RUN_EXAMPLE_1 = true;
    static final boolean RUN_EXAMPLE_2 = true;
    static final boolean RUN_EXAMPLE_3 = true;
    static final boolean RUN_EXAMPLE_4 = true;
    static final boolean RUN_EXAMPLE_5 = true;
    static final boolean RUN_EXAMPLE_6 = true;
    static final boolean RUN_EXAMPLE_7 = true;
    static final boolean RUN_EXAMPLE_8 = true;
    static final boolean RUN_EXAMPLE_9 = true;
    static final boolean RUN_EXAMPLE_10 = true;

    // Example record for JSON mapping (strict - all fields must match)
    record User(String name, int age, String email) {}
    record Post(int id, String title, String body, int userId) {}

    // Example record with @JsonIgnoreProperties (lenient - ignores unknown fields)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PartialPost(int id, String title) {}

    // Example Lombok mutable class with fluent accessors (complete fields)
    @Data
    @Accessors(fluent = true, chain = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class Product {
        Long id;
        String title;
        Double price;
        String description;
        String category;
        String image;
        Rating rating;
    }

    @Data
    @Accessors(fluent = true, chain = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class Rating {
        Double rate;
        Integer count;
    }

    // Example Lombok class ignoring unknown fields
    @Data
    @Accessors(fluent = true, chain = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PartialProduct {
        Long id;
        String title;
        Double price;
        String category;
    }

    public static void main(String[] args) {
        System.out.println("=== TerseHttp Examples ===\n");

        // Example 1: Simple GET request
        if (RUN_EXAMPLE_1) example1_simpleGet();

        // Example 2: GET with headers
        if (RUN_EXAMPLE_2) example2_getWithHeaders();

        // Example 3: POST with JSON
        if (RUN_EXAMPLE_3) example3_postWithJson();

        // Example 4: Error handling with pattern matching
        if (RUN_EXAMPLE_4) example4_errorHandling();

        // Example 5: Using helper methods (orElseThrow, toOptional)
        if (RUN_EXAMPLE_5) example5_helperMethods();

        // Example 6: Complex nested JSON
        if (RUN_EXAMPLE_6) example6_complexJson();

        // Example 7: Authentication examples
        if (RUN_EXAMPLE_7) example7_authentication();

        // Example 8: PUT and DELETE operations
        if (RUN_EXAMPLE_8) example8_putAndDelete();

        // Example 9: Lombok fluent accessors
        if (RUN_EXAMPLE_9) example9_lombokFluent();

        // Example 10: JsonNode for exploratory testing and prototyping
        if (RUN_EXAMPLE_10) example10_jsonNode();

        // Clean exit to avoid OkHttp thread linger warnings
        System.exit(0);
    }

    /**
     * Example 1: Simple GET request with two-way pattern match
     */
    static void example1_simpleGet() {
        System.out.println("--- Example 1: Simple GET (Two-way match) ---");

        var result = req("https://jsonplaceholder.typicode.com/posts/1")
            .json(Post.class);

        // Simple two-way split: Success or Failure
        switch (result) {
            case Success<Post> s ->
                System.out.println("Success! Title: " + s.data().title());
            case Failure f ->
                System.err.println("Failed: " + f.message());
        }
        System.out.println();
    }

    /**
     * Example 2: GET with headers using handle() method
     */
    static void example2_getWithHeaders() {
        System.out.println("--- Example 2: GET with Headers (using handle()) ---");

        var result = req("https://jsonplaceholder.typicode.com/users/1")
            .header("Accept", "application/json")
            .header("User-Agent", "TerseHttp/1.0")
            .json(User.class);

        // Using handle() for concise two-way branching
        result.handle(
            success -> System.out.println("User: " + success.data().name()),
            failure -> System.err.println("Failed: " + failure.message())
        );
        System.out.println();
    }

    /**
     * Example 3: POST with JSON body using orElseThrow()
     */
    static void example3_postWithJson() {
        System.out.println("--- Example 3: POST with JSON (using orElseThrow()) ---");

        try {
            var post = req("https://jsonplaceholder.typicode.com/posts")
                .post()
                .body(obj(
                    "title", "My New Post",
                    "body", "This is the content of my post",
                    "userId", 1
                ))
                .json(Post.class)
                .orElseThrow();

            System.out.println("Created post with ID: " + post.id());
        } catch (Exception e) {
            System.err.println("Failed to create post: " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Example 4: Detailed four-way error handling with parseAs()
     */
    static void example4_errorHandling() {
        System.out.println("--- Example 4: Detailed Error Handling (Four-way match) ---");

        // Try to access a non-existent resource
        var result = req("https://jsonplaceholder.typicode.com/posts/99999")
            .json(Post.class);

        // Detailed four-way pattern matching
        switch (result) {
            case Success<Post> s ->
                System.out.println("Got post: " + s.data().title());
            case NetworkFailure nf ->
                System.err.println("Network failed (retry might help): " + nf.message());
            case HttpFailure hf -> {
                System.out.println("HTTP Error " + hf.status() + " (This is expected for non-existent resource)");
                // Try to parse error response as structured JSON
                record ApiError(String message) {}
                hf.parseAs(ApiError.class).ifPresent(err ->
                    System.out.println("  API Error message: " + err.message())
                );
            }
            case ParseFailure pf ->
                System.err.println("Parse failed (our bug?): " + pf.message());
        }
        System.out.println();
    }

    /**
     * Example 5: Using helper methods
     */
    static void example5_helperMethods() {
        System.out.println("--- Example 5: Helper Methods ---");

        try {
            // orElseThrow - get value or throw exception
            var post = req("https://jsonplaceholder.typicode.com/posts/1")
                .json(Post.class)
                .orElseThrow();
            System.out.println("Post title: " + post.title());
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }

        // toOptional - get Optional<T>
        var maybePost = req("https://jsonplaceholder.typicode.com/posts/2")
            .json(Post.class)
            .toOptional();

        maybePost.ifPresent(post ->
            System.out.println("Found post: " + post.title())
        );
        System.out.println();
    }

    /**
     * Example 6: Complex nested JSON with arrays
     */
    static void example6_complexJson() {
        System.out.println("--- Example 6: Complex Nested JSON ---");

        var complexJson = obj(
            "user", obj(
                "name", "Alice",
                "age", 30,
                "email", "alice@example.com",
                "address", obj(
                    "street", "123 Main St",
                    "city", "NYC",
                    "zip", "10001"
                )
            ),
            "tags", arr("admin", "verified", "premium"),
            "scores", arr(95, 87, 92, 88),
            "metadata", obj(
                "created", "2024-01-01",
                "lastLogin", "2024-11-19",
                "preferences", obj(
                    "theme", "dark",
                    "notifications", true
                )
            )
        );

        System.out.println("Complex JSON:");
        System.out.println(complexJson.toString());
        System.out.println();
    }

    /**
     * Example 7: Authentication with Bearer token and Basic auth
     */
    static void example7_authentication() {
        System.out.println("--- Example 7: Authentication ---");

        // Bearer token authentication
        System.out.println("Bearer Token:");
        req("https://api.github.com/user")
            .bearer("ghp_fake_token_example")
            .json(Object.class)
            .handle(
                success -> System.out.println("  Authenticated successfully"),
                failure -> System.out.println("  Auth failed (expected): " + failure.message())
            );

        // Basic authentication
        System.out.println("\nBasic Auth:");
        req("https://httpbin.org/basic-auth/user/pass")
            .basic("user", "pass")
            .send()
            .handle(
                success -> System.out.println("  Basic auth successful"),
                failure -> System.out.println("  Auth failed: " + failure.message())
            );

        // Using cookies
        System.out.println("\nWith Cookies:");
        req("https://httpbin.org/cookies")
            .cookie("session", "abc123")
            .cookie("preference", "dark-mode")
            .send()
            .toOptional()
            .ifPresent(body -> System.out.println("  Response: " + body.substring(0, Math.min(100, body.length()))));

        System.out.println();
    }

    /**
     * Example 8: PUT and DELETE operations
     */
    static void example8_putAndDelete() {
        System.out.println("--- Example 8: PUT and DELETE Operations ---");

        // PUT request
        System.out.println("PUT request:");
        req("https://jsonplaceholder.typicode.com/posts/1")
            .put()
            .body(obj(
                "id", 1,
                "title", "Updated Title",
                "body", "Updated content",
                "userId", 1
            ))
            .json(Post.class)
            .handle(
                success -> System.out.println("  Updated: " + success.data().title()),
                failure -> System.err.println("  Failed: " + failure.message())
            );

        // DELETE request
        System.out.println("\nDELETE request:");
        req("https://jsonplaceholder.typicode.com/posts/1")
            .delete()
            .send()
            .handle(
                success -> System.out.println("  Deleted successfully (status: " + success.status() + ")"),
                failure -> System.err.println("  Failed: " + failure.message())
            );

        System.out.println();
    }

    /**
     * Example 9: Records and Lombok - 4 variations of JSON mapping
     */
    static void example9_lombokFluent() {
        System.out.println("--- Example 9: Records vs Lombok (4 variations) ---");

        // Variation 1: Record (strict - all fields must match)
        System.out.println("1. Record (strict - requires all fields):");
        req("https://jsonplaceholder.typicode.com/posts/1")
            .json(Post.class)
            .handle(
                success -> {
                    Post p = success.data();
                    System.out.println("  Title: " + p.title());
                    System.out.println("  Body: " + p.body().substring(0, 40) + "...");
                },
                failure -> System.err.println("  Failed: " + failure.message())
            );

        // Variation 2: Record with @JsonIgnoreProperties (lenient)
        System.out.println("\n2. Record with @JsonIgnoreProperties (ignores extra fields):");
        req("https://jsonplaceholder.typicode.com/posts/2")
            .json(PartialPost.class)
            .handle(
                success -> {
                    PartialPost p = success.data();
                    System.out.println("  ID: " + p.id());
                    System.out.println("  Title: " + p.title());
                    System.out.println("  (Note: 'body' and 'userId' were ignored but available in rawJson)");

                    // Access missing fields via rawJson
                    String body = success.rawJson().get("body").asText();
                    int userId = success.rawJson().get("userId").asInt();
                    System.out.println("  Via rawJson - userId: " + userId);
                    System.out.println("  Via rawJson - body: " + body.substring(0, 40) + "...");
                },
                failure -> System.err.println("  Failed: " + failure.message())
            );

        // Variation 3: Lombok with fluent accessors (complete fields)
        System.out.println("\n3. Lombok @Data with fluent accessors (complete):");
        req("https://fakestoreapi.com/products/1")
            .json(Product.class)
            .handle(
                success -> {
                    Product p = success.data();
                    System.out.println("  Product: " + p.title());
                    System.out.println("  Price: $" + p.price());
                    System.out.println("  Rating: " + p.rating().rate() + " (" + p.rating().count() + " reviews)");

                    // Demonstrate fluent chaining (mutation)
                    p.price(p.price() * 0.9).description("SALE: " + p.description());
                    System.out.println("  After 10% discount: $" + p.price());
                },
                failure -> System.err.println("  Failed: " + failure.message())
            );

        // Variation 4: Lombok with @JsonIgnoreProperties (partial)
        System.out.println("\n4. Lombok with @JsonIgnoreProperties (partial):");
        req("https://fakestoreapi.com/products/2")
            .json(PartialProduct.class)
            .handle(
                success -> {
                    PartialProduct p = success.data();
                    System.out.println("  Product: " + p.title());
                    System.out.println("  Price: $" + p.price());
                    System.out.println("  Category: " + p.category());

                    // Fluent mutation
                    p.price(p.price() * 1.1);  // 10% markup
                    System.out.println("  After markup: $" + p.price());
                    System.out.println("  (Note: 'rating', 'description', 'image' were ignored)");
                },
                failure -> System.err.println("  Failed: " + failure.message())
            );

        System.out.println();
    }

    /**
     * Example 10: Using jsonNode() for exploratory testing and prototyping
     *
     * Use cases:
     * 1. Exploratory API testing - when you don't know the response structure
     * 2. Quick prototyping - no need to define records for one-off scripts
     * 3. Dynamic response structures - when response format varies
     */
    static void example10_jsonNode() {
        System.out.println("--- Example 10: JsonNode for Exploratory Testing ---");

        // Use Case 1: Exploratory API testing - inspect unknown response structure
        System.out.println("1. Exploratory Testing (inspect full response):");
        req("https://jsonplaceholder.typicode.com/posts/1")
            .jsonNode()
            .handle(
                success -> {
                    var node = success.data();
                    System.out.println("  Full JSON structure:");
                    System.out.println("  " + node.toPrettyString());
                },
                failure -> System.err.println("  Failed: " + failure.message())
            );

        // Use Case 2: Quick prototyping - extract specific fields without defining records
        System.out.println("\n2. Quick Prototyping (extract specific fields):");
        req("https://jsonplaceholder.typicode.com/users/1")
            .jsonNode()
            .map(node -> node.get("name").asText())  // Extract just the name field
            .handle(
                success -> System.out.println("  User name: " + success.data()),
                failure -> System.err.println("  Failed: " + failure.message())
            );

        // Use Case 3: Dynamic response structures - handle conditional JSON
        System.out.println("\n3. Dynamic Response Handling:");
        req("https://jsonplaceholder.typicode.com/comments/1")
            .jsonNode()
            .handle(
                success -> {
                    var node = success.data();
                    if (node.has("email")) {
                        System.out.println("  Has email field: " + node.get("email").asText());
                    }
                    if (node.has("body")) {
                        String body = node.get("body").asText();
                        System.out.println("  Comment: " + body.substring(0, Math.min(50, body.length())) + "...");
                    }
                    if (node.has("error")) {
                        System.out.println("  Has error: " + node.get("error").asText());
                    } else {
                        System.out.println("  No error field (success case)");
                    }
                },
                failure -> System.err.println("  Failed: " + failure.message())
            );

        // Use Case 4: Extract nested data without full structure
        System.out.println("\n4. Extract Nested Data:");
        req("https://jsonplaceholder.typicode.com/users/2")
            .jsonNode()
            .handle(
                success -> {
                    var node = success.data();
                    String name = node.get("name").asText();
                    String city = node.get("address").get("city").asText();
                    String companyName = node.get("company").get("name").asText();
                    System.out.println("  " + name + " lives in " + city);
                    System.out.println("  Works at: " + companyName);
                },
                failure -> System.err.println("  Failed: " + failure.message())
            );

        System.out.println();
    }
}
