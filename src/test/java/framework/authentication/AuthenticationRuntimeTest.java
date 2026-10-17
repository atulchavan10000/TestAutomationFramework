package framework.authentication;

import authentication.AuthenticationRuntimeFactory;
import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.RequestOptions;
import framework.config.AuthenticationConfig;
import framework.config.FrameworkConfig;
import framework.config.ServiceConfig;
import framework.context.TestContext;
import framework.http.HttpClient;
import framework.http.HttpHeaders;
import framework.http.HttpMethod;
import framework.http.HttpRequest;
import framework.http.HttpResponse;
import framework.serialization.JacksonJsonCodec;
import org.testng.annotations.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Deterministic verification of authentication lifecycle and execution policies. */
public final class AuthenticationRuntimeTest {

    @Test(groups = "framework")
    public void staticBearerProfileFlowsThroughTheCompleteClientPipeline() {
        AtomicInteger sends = new AtomicInteger();
        AtomicReference<HttpRequest> received = new AtomicReference<>();
        HttpClient transport = request -> {
            sends.incrementAndGet();
            received.set(request);
            return response(200);
        };
        AuthenticationConfig authentication = new AuthenticationConfig(
                Map.of("static-provider", new AuthenticationConfig.StaticTokenProvider(
                        "static-provider", "static-token-secret")),
                Map.of("static-bearer", new AuthenticationConfig.BearerProfile(
                        "static-bearer", "static-provider")));
        FrameworkConfig config = new FrameworkConfig("qa", Duration.ofSeconds(5), false, 1000,
                true, authentication, Map.of("protected-service", new ServiceConfig(
                        "protected-service", URI.create("https://service.example.test"),
                        Duration.ofSeconds(5), "static-bearer")));
        AuthenticationRuntime runtime = AuthenticationRuntimeFactory.create(config, reference -> {
            assertThat(reference, is("static-token-secret"));
            return "fixed-token";
        });

        HttpResponse result = client(runtime, transport).executeRaw(profiledRequest("static-bearer"));

        assertThat(result.statusCode(), is(200));
        assertThat(sends.get(), is(1));
        assertThat(received.get().headers().firstValue("Authorization").orElseThrow(),
                is("Bearer fixed-token"));
    }

    @Test(groups = "framework")
    public void parallelCacheMissPerformsOneTokenAcquisition() throws Exception {
        AtomicInteger acquisitions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        TokenAcquirer acquirer = context -> {
            acquisitions.incrementAndGet();
            return new TokenGrant("shared-token", null, Duration.ofMinutes(5));
        };
        CachingAccessTokenProvider provider = new CachingAccessTokenProvider(
                acquirer, Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC));
        TestContext context = context();
        ExecutorService workers = Executors.newFixedThreadPool(12);

        try {
            List<Future<String>> results = java.util.stream.IntStream.range(0, 24)
                    .mapToObj(ignored -> workers.submit(() -> {
                        start.await();
                        return provider.accessToken(context);
                    }))
                    .toList();
            start.countDown();

            for (Future<String> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS), is("shared-token"));
            }
            assertThat(acquisitions.get(), is(1));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test(groups = "framework")
    public void tokenRemainsUsableBeforeBoundaryAndRefreshesAtBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-17T00:00:00Z"));
        AtomicInteger acquisitions = new AtomicInteger();
        TokenAcquirer acquirer = context -> new TokenGrant(
                "token-" + acquisitions.incrementAndGet(), null, Duration.ofSeconds(100));
        CachingAccessTokenProvider provider = new CachingAccessTokenProvider(acquirer, clock);
        TestContext context = context();

        assertThat(provider.accessToken(context), is("token-1"));
        clock.advance(Duration.ofSeconds(89));
        assertThat(provider.accessToken(context), is("token-1"));
        clock.advance(Duration.ofSeconds(1));
        assertThat(provider.accessToken(context), is("token-2"));
        assertThat(acquisitions.get(), is(2));
    }

    @Test(groups = "framework")
    public void refreshCapableProviderUsesRefreshTokenAtBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-17T00:00:00Z"));
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        TokenAcquirer acquirer = new TokenAcquirer() {
            @Override public TokenGrant acquire(TestContext context) {
                acquisitions.incrementAndGet();
                return new TokenGrant("initial", "refresh-value", Duration.ofSeconds(20));
            }

            @Override public boolean supportsRefresh() { return true; }

            @Override public TokenGrant refresh(String refreshToken, TestContext context) {
                refreshes.incrementAndGet();
                assertThat(refreshToken, is("refresh-value"));
                return new TokenGrant("refreshed", "next-refresh-value", Duration.ofSeconds(20));
            }
        };
        CachingAccessTokenProvider provider = new CachingAccessTokenProvider(acquirer, clock);
        TestContext context = context();

        assertThat(provider.accessToken(context), is("initial"));
        clock.advance(Duration.ofSeconds(18));
        assertThat(provider.accessToken(context), is("refreshed"));
        assertThat(acquisitions.get(), is(1));
        assertThat(refreshes.get(), is(1));
    }

    @Test(groups = "framework")
    public void unauthorizedResponseIsReturnedWithoutRefreshOrReplay() {
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger sends = new AtomicInteger();
        TokenAcquirer acquirer = new TokenAcquirer() {
            @Override public TokenGrant acquire(TestContext context) {
                acquisitions.incrementAndGet();
                return new TokenGrant("rejected-token", "refresh-value", Duration.ofMinutes(5));
            }

            @Override public boolean supportsRefresh() { return true; }

            @Override public TokenGrant refresh(String refreshToken, TestContext context) {
                refreshes.incrementAndGet();
                return new TokenGrant("replacement-token", refreshToken, Duration.ofMinutes(5));
            }
        };
        AuthenticationRuntime runtime = new AuthenticationRuntime(Map.of(
                "protected", new BearerAuthenticator(new CachingAccessTokenProvider(acquirer))));
        HttpClient rejectingTransport = request -> {
            sends.incrementAndGet();
            return response(401);
        };

        HttpResponse result = client(runtime, rejectingTransport).executeRaw(profiledRequest("protected"));

        assertThat(result.statusCode(), is(401));
        assertThat(acquisitions.get(), is(1));
        assertThat(refreshes.get(), is(0));
        assertThat(sends.get(), is(1));
    }

    private static ApiRequest profiledRequest(String profile) {
        return new ApiRequest(HttpMethod.GET, "/protected", null,
                RequestOptions.builder().authenticationProfile(profile).build());
    }

    private static ApiClient client(AuthenticationRuntime runtime, HttpClient transport) {
        return new ApiClient(URI.create("https://service.example.test"), Duration.ofSeconds(5),
                new JacksonJsonCodec(), transport, List.of(), runtime, null, context());
    }

    private static TestContext context() {
        return new TestContext(UUID.randomUUID().toString());
    }

    private static HttpResponse response(int status) {
        return new HttpResponse(status, HttpHeaders.empty(), new byte[0], Duration.ofMillis(1));
    }

    /** Controllable clock used to test the exact early-refresh boundary without sleeping. */
    private static final class MutableClock extends Clock {
        private final ZoneId zone;
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this(instant, ZoneOffset.UTC);
        }

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return zone; }

        @Override public Clock withZone(ZoneId newZone) { return new MutableClock(instant, newZone); }

        @Override public Instant instant() { return instant; }
    }
}
