package terse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Terse HTTP client wrapper providing minimal ceremony REST API calls.
 */
public class Rest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ======================
    // JSON Builder Classes
    // ======================

    /**
     * Fluent JSON object builder that extends HashMap.
     * Supports nested objects and arrays for terse JSON construction.
     */
    public static class JsonObj extends HashMap<String, Object> {

        /**
         * Creates a JSON object from varargs key-value pairs.
         * @param vs alternating keys and values (must be even length)
         */
        public JsonObj(Object... vs) {
            if (vs.length % 2 != 0) {
                throw new IllegalArgumentException("Arguments must be key-value pairs (even length)");
            }
            for (int i = 0; i < vs.length; i += 2) {
                put((String) vs[i], vs[i + 1]);
            }
        }

        @Override
        public String toString() {
            try {
                return MAPPER.writeValueAsString(this);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize JSON object", e);
            }
        }

        /**
         * Converts this JSON object to an OkHttp RequestBody.
         * @return RequestBody with application/json media type
         */
        public RequestBody body() {
            return RequestBody.create(
                toString(),
                MediaType.get("application/json")
            );
        }
    }

    /**
     * Fluent JSON array builder that extends ArrayList.
     * Supports nested objects and arrays for terse JSON construction.
     */
    public static class JsonArr extends ArrayList<Object> {

        /**
         * Creates a JSON array from varargs elements.
         * @param vs array elements
         */
        public JsonArr(Object... vs) {
            addAll(Arrays.asList(vs));
        }

        @Override
        public String toString() {
            try {
                return MAPPER.writeValueAsString(this);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize JSON array", e);
            }
        }
    }

    // ======================
    // Static Helper Methods
    // ======================

    /**
     * Creates a JSON object from varargs key-value pairs.
     * @param vs alternating keys and values
     * @return JsonObj instance
     */
    public static JsonObj obj(Object... vs) {
        return new JsonObj(vs);
    }

    /**
     * Creates a JSON array from varargs elements.
     * @param vs array elements
     * @return JsonArr instance
     */
    public static JsonArr arr(Object... vs) {
        return new JsonArr(vs);
    }

    /**
     * Creates a new fluent HTTP request builder.
     * @param url the target URL
     * @return Rest instance for fluent configuration
     */
    public static RestClient req(String url) {
        return new RestClient(url);
    }

    // ======================
    // Error Handling Types
    // ======================

    /**
     * Represents the result of an HTTP request.
     * Either Success (with parsed data) or one of three failure types.
     */
    public sealed interface HttpResponse<T>
        permits Success, NetworkFailure, HttpFailure, ParseFailure {

        /**
         * Unwraps the value or throws an exception.
         * @return the successful value
         * @throws RuntimeException if there was any error
         */
        default T orElseThrow() {
            return switch(this) {
                case Success<T> s -> s.data();
                case NetworkFailure nf -> throw new RuntimeException(nf.message());
                case HttpFailure hf -> throw new RuntimeException(hf.message());
                case ParseFailure pf -> throw new RuntimeException(pf.message());
            };
        }

        /**
         * Converts the result to an Optional.
         * @return Optional containing the value if successful, empty otherwise
         */
        default Optional<T> toOptional() {
            return switch(this) {
                case Success<T> s -> Optional.of(s.data());
                case NetworkFailure nf -> Optional.empty();
                case HttpFailure hf -> Optional.empty();
                case ParseFailure pf -> Optional.empty();
            };
        }

        /**
         * Maps the successful value to another type.
         * @param fn mapping function
         * @return new HttpResponse with mapped value
         */
        default <U> HttpResponse<U> map(Function<T, U> fn) {
            return switch(this) {
                case Success<T> s -> new Success<>(
                    fn.apply(s.data()),
                    s.rawJson(),
                    s.status(),
                    s.headers()
                );
                case NetworkFailure nf -> nf;
                case HttpFailure hf -> hf;
                case ParseFailure pf -> pf;
            };
        }

        /**
         * Handle success and failure cases with consumers.
         * @param onSuccess consumer for success case
         * @param onFailure consumer for failure case
         */
        default void handle(Consumer<Success<T>> onSuccess, Consumer<Failure> onFailure) {
            switch(this) {
                case Success<T> s -> onSuccess.accept(s);
                default -> onFailure.accept((Failure) this);
            }
        }
    }

    /**
     * Successful HTTP response with parsed data.
     *
     * @param data Parsed response as the requested type
     * @param rawJson Complete JSON response as JsonNode (access fields not in T)
     * @param status HTTP status code
     * @param headers Response headers
     */
    public record Success<T>(
        T data,
        JsonNode rawJson,
        int status,
        Map<String, List<String>> headers
    ) implements HttpResponse<T> {}

    /**
     * Base interface for all failure types.
     * Provides common error handling methods.
     */
    public sealed interface Failure
        permits NetworkFailure, HttpFailure, ParseFailure {

        /**
         * Returns a human-readable error message.
         * @return error message
         */
        String message();
    }

    /**
     * Network-level failure - connection failed (timeout, DNS, unreachable, etc.).
     */
    public record NetworkFailure(Exception cause) implements HttpResponse, Failure {
        @Override
        public String message() {
            return "Network error: " + cause.getMessage();
        }
    }

    /**
     * HTTP-level failure - server returned error status code (4xx, 5xx).
     */
    public record HttpFailure(
        int status,
        String rawBody,
        Map<String, List<String>> headers
    ) implements HttpResponse, Failure {

        @Override
        public String message() {
            return "HTTP " + status + ": " + rawBody;
        }

        /**
         * Attempts to parse the error response body as a structured JSON object.
         * @param errorType the class to deserialize into
         * @return Optional containing the parsed error object, or empty if parsing fails
         */
        public <E> Optional<E> parseAs(Class<E> errorType) {
            try {
                return Optional.of(MAPPER.readValue(rawBody, errorType));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    /**
     * Parse-level failure - HTTP was successful (2xx) but couldn't parse response as expected type.
     */
    public record ParseFailure(
        int status,
        String rawBody,
        Exception cause,
        Map<String, List<String>> headers
    ) implements HttpResponse, Failure {

        @Override
        public String message() {
            return "Parse error: " + cause.getMessage();
        }
    }

    // ======================
    // Fluent REST Client
    // ======================

    /**
     * Fluent HTTP request builder.
     * Provides curl-like API for making HTTP requests.
     */
    public static class RestClient {

        private static final OkHttpClient DEFAULT_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

        private final String url;
        private final Request.Builder builder;
        private RequestBody requestBody;
        private String method;
        private OkHttpClient client;
        private final List<String> cookies;

        private RestClient(String url) {
            this.url = url;
            this.builder = new Request.Builder();
            this.method = "GET";
            this.client = DEFAULT_CLIENT;
            this.cookies = new ArrayList<>();
        }

        /**
         * Sets a custom OkHttpClient.
         * @param client custom client
         * @return this
         */
        public RestClient client(OkHttpClient client) {
            this.client = client;
            return this;
        }

        /**
         * Adds a header to the request.
         * @param key header name
         * @param value header value
         * @return this
         */
        public RestClient header(String key, String value) {
            builder.header(key, value);
            return this;
        }

        /**
         * Adds multiple headers to the request.
         * @param headers map of headers
         * @return this
         */
        public RestClient headers(Map<String, String> headers) {
            headers.forEach(this::header);
            return this;
        }

        /**
         * Adds Bearer token authorization header.
         * @param token the token value
         * @return this
         */
        public RestClient bearer(String token) {
            return header("Authorization", "Bearer " + token);
        }

        /**
         * Adds Basic authentication header.
         * @param username username
         * @param password password
         * @return this
         */
        public RestClient basic(String username, String password) {
            String credentials = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            return header("Authorization", "Basic " + encoded);
        }

        /**
         * Sets Content-Type header.
         * @param type content type
         * @return this
         */
        public RestClient contentType(String type) {
            return header("Content-Type", type);
        }

        /**
         * Adds a cookie to the request.
         * @param name cookie name
         * @param value cookie value
         * @return this
         */
        public RestClient cookie(String name, String value) {
            cookies.add(name + "=" + value);
            return this;
        }

        /**
         * Sets the request method to GET.
         * @return this
         */
        public RestClient get() {
            this.method = "GET";
            return this;
        }

        /**
         * Sets the request method to POST.
         * @return this
         */
        public RestClient post() {
            this.method = "POST";
            return this;
        }

        /**
         * Sets the request method to PUT.
         * @return this
         */
        public RestClient put() {
            this.method = "PUT";
            return this;
        }

        /**
         * Sets the request method to DELETE.
         * @return this
         */
        public RestClient delete() {
            this.method = "DELETE";
            return this;
        }

        /**
         * Sets the request method to PATCH.
         * @return this
         */
        public RestClient patch() {
            this.method = "PATCH";
            return this;
        }

        /**
         * Sets the request method to HEAD.
         * @return this
         */
        public RestClient head() {
            this.method = "HEAD";
            return this;
        }

        /**
         * Sets the request body from a JsonObj.
         * Automatically sets Content-Type to application/json.
         * @param json JSON object
         * @return this
         */
        public RestClient body(JsonObj json) {
            this.requestBody = json.body();
            contentType("application/json");
            return this;
        }

        /**
         * Sets the request body from a raw string.
         * @param raw raw body content
         * @return this
         */
        public RestClient body(String raw) {
            this.requestBody = RequestBody.create(raw, MediaType.get("text/plain"));
            return this;
        }

        /**
         * Sets the request body with custom media type.
         * @param content body content
         * @param mediaType media type
         * @return this
         */
        public RestClient body(String content, String mediaType) {
            this.requestBody = RequestBody.create(content, MediaType.get(mediaType));
            return this;
        }

        /**
         * Executes the request and returns the raw string response.
         * @return HttpResponse containing the response string
         */
        public HttpResponse<String> send() {
            try {
                builder.url(url);

                // Add cookies header if any
                if (!cookies.isEmpty()) {
                    builder.header("Cookie", String.join("; ", cookies));
                }

                // Handle request body for different methods
                Request request;
                if ("GET".equals(method) || "HEAD".equals(method)) {
                    request = builder.method(method, null).build();
                } else {
                    RequestBody body = requestBody != null ? requestBody
                        : RequestBody.create("", null);
                    request = builder.method(method, body).build();
                }

                Response response = client.newCall(request).execute();
                String bodyString = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    // Parse as JsonNode for raw access
                    JsonNode jsonNode;
                    try {
                        jsonNode = MAPPER.readTree(bodyString);
                    } catch (Exception e) {
                        // If not valid JSON, create a text node
                        jsonNode = MAPPER.getNodeFactory().textNode(bodyString);
                    }

                    return new Success<>(
                        bodyString,
                        jsonNode,
                        response.code(),
                        response.headers().toMultimap()
                    );
                } else {
                    return new HttpFailure(
                        response.code(),
                        bodyString,
                        response.headers().toMultimap()
                    );
                }
            } catch (IOException e) {
                return new NetworkFailure(e);
            }
        }

        /**
         * Executes the request and parses the response as JSON.
         * @param type the class to deserialize into
         * @return HttpResponse containing the parsed object
         */
        public <T> HttpResponse<T> json(Class<T> type) {
            try {
                builder.url(url);

                // Add cookies header if any
                if (!cookies.isEmpty()) {
                    builder.header("Cookie", String.join("; ", cookies));
                }

                // Handle request body for different methods
                Request request;
                if ("GET".equals(method) || "HEAD".equals(method)) {
                    request = builder.method(method, null).build();
                } else {
                    RequestBody body = requestBody != null ? requestBody
                        : RequestBody.create("", null);
                    request = builder.method(method, body).build();
                }

                Response response = client.newCall(request).execute();
                String bodyString = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    try {
                        // Parse as both T and JsonNode
                        T parsed = MAPPER.readValue(bodyString, type);
                        JsonNode jsonNode = MAPPER.readTree(bodyString);

                        return new Success<>(
                            parsed,
                            jsonNode,
                            response.code(),
                            response.headers().toMultimap()
                        );
                    } catch (Exception parseError) {
                        return new ParseFailure(
                            response.code(),
                            bodyString,
                            parseError,
                            response.headers().toMultimap()
                        );
                    }
                } else {
                    return new HttpFailure(
                        response.code(),
                        bodyString,
                        response.headers().toMultimap()
                    );
                }
            } catch (IOException e) {
                return new NetworkFailure(e);
            }
        }
    }
}
