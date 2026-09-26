package framework.client;
import framework.config.FrameworkConfig;
import framework.config.ServiceConfig;
import framework.context.TestContext;
import framework.http.HttpHeaders;
import framework.http.restassured.RestAssuredHttpClient;
import framework.interceptor.CommonHeadersInterceptor;
import framework.interceptor.CorrelationIdInterceptor;
import framework.interceptor.LoggingInterceptor;
import framework.logging.JacksonBodyLogFormatter;
import framework.serialization.JacksonJsonCodec;
import java.net.URI;
import java.time.Duration;
import java.util.List;
/** Minimal explicit wiring. Each test invocation calls this with a fresh context. */
public final class ApiClientFactory {
    private ApiClientFactory() {}

    /**
     * Builds a client for one named service from the suite's immutable configuration.
     * Product tests choose the service; they do not read YAML or environment variables.
     */
    public static ApiClient forTest(
            FrameworkConfig config,
            String serviceName,
            TestContext context) {

        ServiceConfig service = config.service(serviceName);
        return forTest(
                service.baseUri(), service.timeout(), context, config.logBodies(),
                config.maxBodyLogCharacters());
    }

    /**
     * Lower-level overload retained for callers that already have explicit values.
     * Normal TestNG wiring should use the FrameworkConfig overload above.
     */
    public static ApiClient forTest(URI baseUri, Duration timeout, TestContext context, boolean logBodies) {
        return forTest(baseUri, timeout, context, logBodies, 8000);
    }

    /** Builds a client with an explicit body-logging character limit. */
    public static ApiClient forTest(
            URI baseUri,
            Duration timeout,
            TestContext context,
            boolean logBodies,
            int maxBodyLogCharacters) {
        HttpHeaders common = HttpHeaders.builder().set("Accept", "application/json").build();
        return new ApiClient(baseUri, timeout, new JacksonJsonCodec(), new RestAssuredHttpClient(),
                List.of(new CommonHeadersInterceptor(common),
                        new CorrelationIdInterceptor(context),
                        new LoggingInterceptor(context, "X-Correlation-ID",
                                logBodies
                                        ? JacksonBodyLogFormatter.defaults(maxBodyLogCharacters)
                                        : null)));
    }
}
