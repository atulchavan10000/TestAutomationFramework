package framework.client;

import framework.http.HttpHeaders;
import framework.http.HttpRequest;
import framework.http.HttpResponse;
import framework.http.HttpClient;
import framework.interceptor.DefaultInterceptorChain;
import framework.interceptor.HttpInterceptor;
import framework.serialization.JsonCodec;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Coordinates request preparation, interceptor execution, and response mapping.
 * Test-specific expectations stay outside this class.
 *
 * When interceptors contain a test-scoped TestContext, create one ApiClient for
 * each test invocation rather than sharing that client between tests.
 */
public final class ApiClient {
    private final RequestUriResolver uriResolver;
    private final Duration defaultTimeout;
    private final JsonCodec codec;
    private final HttpClient transport;
    private final List<HttpInterceptor> interceptors;

    public ApiClient(
            URI baseUri,
            Duration defaultTimeout,
            JsonCodec codec,
            HttpClient transport,
            List<HttpInterceptor> interceptors) {

        this.uriResolver = new RequestUriResolver(baseUri);
        this.defaultTimeout = Objects.requireNonNull(
                defaultTimeout, "Default timeout must not be null");
        if (defaultTimeout.isNegative() || defaultTimeout.isZero()) {
            throw new IllegalArgumentException("Default timeout must be positive");
        }
        this.codec = Objects.requireNonNull(codec, "JSON codec must not be null");
        this.transport = Objects.requireNonNull(transport, "HTTP transport must not be null");
        this.interceptors = List.copyOf(
                Objects.requireNonNull(interceptors, "Interceptors must not be null"));
    }

    /**
     * Executes the request and maps a non-empty JSON response to responseType.
     * Void.class explicitly skips response-body mapping.
     */
    public <R> ApiResponse<R> execute(ApiRequest request, Class<R> responseType) {
        Objects.requireNonNull(request, "API request must not be null");
        Objects.requireNonNull(responseType, "Response type must not be null");
        if (responseType.isPrimitive()) {
            throw new IllegalArgumentException("Use a reference response type");
        }

        HttpResponse raw = executeRaw(request);
        if (responseType == Void.class || raw.bodyLength() == 0) {
            return new ApiResponse<>(raw, null);
        }

        // Do not try to deserialize an HTML/text error as the expected DTO.
        String mediaType = raw.contentType()
                .orElse("")
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!(mediaType.equals("application/json") || mediaType.endsWith("+json"))) {
            throw new ResponseMappingException(
                    "Response content type is not JSON; inspect rawResponse()", raw, null);
        }

        try {
            return new ApiResponse<>(raw, codec.deserialize(raw.body(), responseType));
        } catch (RuntimeException failure) {
            throw new ResponseMappingException(
                    "Could not map response to " + responseType.getTypeName(), raw, failure);
        }
    }

    /**
     * Prepares and executes a request without mapping its response to a DTO.
     * Use this for raw, text, binary, malformed, or negative-test responses.
     */
    public HttpResponse executeRaw(ApiRequest request) {
        Objects.requireNonNull(request, "API request must not be null");

        RequestOptions options = request.options();
        HttpHeaders.Builder headers = options.headers().toBuilder();
        HttpRequest.Builder prepared = HttpRequest.builder()
                .method(request.method())
                .uri(uriResolver.resolve(request.path(), options))
                .timeout(options.timeout().orElse(defaultTimeout));

        if (request.hasBody()) {
            Object body = request.body();
            if (body instanceof RawBody raw) {
                prepared.body(raw.bytes());
                if (!options.headers().contains("Content-Type")) {
                    headers.set("Content-Type", raw.contentType());
                }
            } else {
                prepared.body(codec.serialize(body));
                if (!options.headers().contains("Content-Type")) {
                    headers.set("Content-Type", "application/json");
                }
            }
        }

        prepared.headers(headers.build());
        return new DefaultInterceptorChain(interceptors, transport)
                .proceed(prepared.build());
    }
}
