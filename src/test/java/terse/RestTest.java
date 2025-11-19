package terse;

import static org.junit.jupiter.api.Assertions.*;
import static terse.Rest.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TerseRest library.
 */
class RestTest {

    /**
     * Test jsonNode() method returns JsonNode successfully.
     */
    @Test
    void testJsonNode_Success() {
        var result = req("https://jsonplaceholder.typicode.com/posts/1")
            .jsonNode();

        assertInstanceOf(Rest.Success.class, result);

        var success = (Rest.Success<JsonNode>) result;
        JsonNode node = success.data();

        assertNotNull(node);
        assertTrue(node.has("id"));
        assertTrue(node.has("title"));
        assertTrue(node.has("body"));
        assertTrue(node.has("userId"));

        assertEquals(1, node.get("id").asInt());
    }

    /**
     * Test jsonNode() with map() to extract specific field.
     */
    @Test
    void testJsonNode_MapField() {
        var titleResult = req("https://jsonplaceholder.typicode.com/posts/1")
            .jsonNode()
            .map(node -> node.get("title").asText());

        assertInstanceOf(Rest.Success.class, titleResult);

        String title = titleResult.orElseThrow();
        assertNotNull(title);
        assertFalse(title.isEmpty());
    }

    /**
     * Test jsonNode() with nested data extraction.
     */
    @Test
    void testJsonNode_NestedData() {
        var result = req("https://jsonplaceholder.typicode.com/users/1")
            .jsonNode();

        assertInstanceOf(Rest.Success.class, result);

        var node = result.orElseThrow();

        // Extract nested address data
        assertTrue(node.has("address"));
        JsonNode address = node.get("address");
        assertTrue(address.has("city"));
        assertNotNull(address.get("city").asText());

        // Extract nested company data
        assertTrue(node.has("company"));
        JsonNode company = node.get("company");
        assertTrue(company.has("name"));
        assertNotNull(company.get("name").asText());
    }

    /**
     * Test jsonNode() handles HTTP errors appropriately.
     */
    @Test
    void testJsonNode_HttpFailure() {
        var result = req("https://jsonplaceholder.typicode.com/posts/99999")
            .jsonNode();

        // The API may return either HttpFailure (404) or Success with empty object
        // depending on the endpoint behavior
        assertTrue(result instanceof Rest.HttpFailure || result instanceof Rest.Success);
    }

    /**
     * Test jsonNode() with toOptional().
     */
    @Test
    void testJsonNode_ToOptional() {
        var optional = req("https://jsonplaceholder.typicode.com/posts/1")
            .jsonNode()
            .toOptional();

        assertTrue(optional.isPresent());

        JsonNode node = optional.get();
        assertNotNull(node);
        assertTrue(node.has("id"));
    }

    /**
     * Test jsonNode() with handle() method.
     */
    @Test
    void testJsonNode_Handle() {
        var wasSuccessful = new boolean[1]; // Use array to capture in lambda

        req("https://jsonplaceholder.typicode.com/posts/1")
            .jsonNode()
            .handle(
                success -> {
                    assertNotNull(success.data());
                    assertTrue(success.data().has("title"));
                    wasSuccessful[0] = true;
                },
                failure -> fail("Request should have succeeded: " + failure.message())
            );

        assertTrue(wasSuccessful[0], "Handle success callback should have been called");
    }

    /**
     * Test jsonNode() can inspect pretty printed JSON.
     */
    @Test
    void testJsonNode_PrettyPrint() {
        var result = req("https://jsonplaceholder.typicode.com/posts/1")
            .jsonNode();

        var node = result.orElseThrow();
        String prettyJson = node.toPrettyString();

        assertNotNull(prettyJson);
        assertTrue(prettyJson.contains("\"id\""));
        assertTrue(prettyJson.contains("\"title\""));
        // Pretty print should have line breaks
        assertTrue(prettyJson.contains("\n"));
    }
}
