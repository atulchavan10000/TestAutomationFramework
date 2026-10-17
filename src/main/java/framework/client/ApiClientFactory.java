package framework.client;
import framework.authentication.AuthenticationRuntime;
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
/**
 * Minimal composition root for an {@link ApiClient}. Each test invocation uses
 * a fresh {@link TestContext}, while the optional authentication runtime may be
 * shared safely for the complete suite.
 */
public final class ApiClientFactory {
    /** Static composition root; callers obtain clients through the factory methods. */
    private ApiClientFactory() {}

    /**
     * Builds an unauthenticated client for one named service from the suite's
     * immutable configuration. Product tests normally use the overload that
     * receives the suite authentication runtime; token acquisition uses this
     * overload to prevent recursive authentication.
     */
    public static ApiClient forTest(
            FrameworkConfig config,
            String serviceName,
            TestContext context) {

        return forTest(config, serviceName, context, null);
    }

    /**
     * Builds a client using the suite-scoped provider/profile registry.
     * Passing {@code null} deliberately omits authentication, which is required
     * for login and token endpoints.
     */
    public static ApiClient forTest(FrameworkConfig config, String serviceName, TestContext context,
            AuthenticationRuntime authenticationRuntime) {

        ServiceConfig service = config.service(serviceName);
        HttpHeaders common = HttpHeaders.builder().set("Accept", "application/json").build();

        // Static interceptors run in declaration order. ApiClient prepends the
        // per-request AuthenticationInterceptor when a runtime is present, so
        // logging observes the final request after authentication, headers, and
        // correlation have been applied.
        return new ApiClient(service.baseUri(), service.timeout(), new JacksonJsonCodec(),
                new RestAssuredHttpClient(),
                List.of(new CommonHeadersInterceptor(common), new CorrelationIdInterceptor(context),
                        new LoggingInterceptor(context, "X-Correlation-ID", config.logBodies()
                                ? JacksonBodyLogFormatter.defaults(config.maxBodyLogCharacters()) : null)),
                authenticationRuntime, service.defaultAuthenticationProfile().orElse(null), context);
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
