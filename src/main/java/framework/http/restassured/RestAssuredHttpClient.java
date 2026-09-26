package framework.http.restassured;

import framework.http.HttpClient;
import framework.http.HttpHeaders;
import framework.http.HttpRequest;
import framework.http.HttpResponse;
import framework.http.TransportException;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.RestAssured;
import io.restassured.config.*;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import java.time.Duration;
import java.util.Objects;

/**
 * Translates framework models to/from REST Assured, without DTO mapping.
 * Each call owns its native client, closing connections after reading the body.
 * No framework retries, redirects, cookies, or credentials are shared across calls.
 *
 * REST Assured 5.x requires the legacy AbstractHttpClient API for a custom client.
 * Deprecated Apache usage is confined here, with automatic retries disabled.
 */
@SuppressWarnings("deprecation")
public final class RestAssuredHttpClient implements HttpClient {
    @Override
    public HttpResponse execute(HttpRequest request) {
        Objects.requireNonNull(request);
        long millis = request.timeout().toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Timeout must be between 1 ms and Integer.MAX_VALUE ms");
        }
        DefaultHttpClient nativeClient = new DefaultHttpClient();
        nativeClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
        long started = System.nanoTime();
        try {
            // timeout is applied separately to connect and socket-read inactivity.
            // It is NOT a whole-request wall-clock deadline (DNS/slow trickle can take longer).
            HeaderConfig headerConfig = HeaderConfig.headerConfig();
            for (String name : request.headers().asMap().keySet()) {
                headerConfig = headerConfig.mergeHeadersWithName(name);
            }
            RestAssuredConfig config = RestAssuredConfig.config()
                    .httpClient(HttpClientConfig.httpClientConfig()
                            .httpClientFactory(() -> nativeClient)
                            .setParam("http.connection.timeout", (int)millis)
                            .setParam("http.socket.timeout", (int)millis))
                    .redirect(RedirectConfig.redirectConfig().followRedirects(false))
                    .encoderConfig(EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false))
                    .headerConfig(headerConfig);
            RequestSpecification preparedSpec = new RequestSpecBuilder()
                    .setConfig(config).setBaseUri(request.uri().getScheme() + "://" + request.uri().getRawAuthority())
                    .setBasePath("").setUrlEncodingEnabled(false).build();
            // A built spec describes settings; given().spec(...) attaches the
            // response specification that REST Assured requires for execution.
            RequestSpecification nativeRequest = RestAssured.given().spec(preparedSpec);
            nativeRequest.noFilters().auth().none();
            request.headers().asMap().forEach((name, values) ->
                    values.forEach(value -> nativeRequest.header(new Header(name, value))));
            if (request.hasBody()) nativeRequest.body(request.body());

            io.restassured.response.Response received =
                    nativeRequest.request(request.method().name(), request.uri().toASCIIString());
            byte[] body = received.asByteArray();
            HttpHeaders.Builder headers = HttpHeaders.builder();
            for (Header header : received.getHeaders()) headers.add(header.getName(), header.getValue());
            return new HttpResponse(received.statusCode(), headers.build(), body,
                    Duration.ofNanos(System.nanoTime() - started));
        } catch (Exception failure) {
            // Groovy can propagate checked I/O exceptions despite the Java DSL's
            // signature. Catch Exception so socket failures are normalized too.
            throw new TransportException("HTTP execution failed for " + request.method() + " " + request.uri().getHost(), failure);
        } finally {
            nativeClient.getConnectionManager().shutdown();
        }
    }
}
